package com.example.dormlaundryalarm.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;

@Service
public class DiscordNotificationService implements NotificationService {
    private final String webhookUrl;
    private final RestClient client;

    public DiscordNotificationService(@Value("${discord.webhook.url:}") String webhookUrl) {
        this.webhookUrl = webhookUrl;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3)).version(HttpClient.Version.HTTP_1_1).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.client = RestClient.builder().requestFactory(factory).build();
    }
    @Override public boolean isConfigured() { return webhookUrl != null && !webhookUrl.isBlank(); }

    @Override
    public DeliveryResult sendLaundryCompleteNotification(Long machineId, String cycleId) {
        if (!isConfigured()) return DeliveryResult.retry("WEBHOOK_NOT_CONFIGURED", Duration.ofSeconds(30));
        URI uri;
        try {
            uri = UriComponentsBuilder.fromUriString(webhookUrl).replaceQueryParam("wait", "true").build().toUri();
            if (uri.getHost() == null || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())))
                return DeliveryResult.failed("WEBHOOK_INVALID");
        }
        catch (RuntimeException ignored) { return DeliveryResult.failed("WEBHOOK_INVALID"); }
        try {
            return client.post().uri(uri).header("User-Agent", "DormLaundryAlarm/0.3")
                    .body(Map.of("content", "🧺 알림: " + machineId + "번 세탁기의 작동 완료가 감지되었습니다. 빨래를 확인해 주세요.\n기록: " + cycleId,
                            "allowed_mentions", Map.of("parse", java.util.List.of())))
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status >= 200 && status < 300) {
                            Map<?, ?> receipt = response.bodyTo(Map.class);
                            Object id = receipt == null ? null : receipt.get("id");
                            // wait=true must acknowledge a saved Discord message.
                            return id == null ? DeliveryResult.uncertain("ACK_WITHOUT_MESSAGE_ID") : DeliveryResult.sent(id.toString());
                        }
                        if (status == 429) {
                            Duration wait = retryAfter(response.getHeaders().getFirst("Retry-After"));
                            try {
                                Map<?, ?> detail = response.bodyTo(Map.class);
                                if (detail != null && detail.get("retry_after") != null) {
                                    Duration bodyWait = retryAfter(detail.get("retry_after").toString());
                                    if (bodyWait.compareTo(wait) > 0) wait = bodyWait;
                                }
                            } catch (RuntimeException ignored) { /* Header/backoff remains usable. */ }
                            return DeliveryResult.retry("HTTP_429", wait.isZero() ? Duration.ofSeconds(60) : wait);
                        }
                        // A server failure can happen after accepting the message. Avoid blind resends.
                        if (status >= 500) return DeliveryResult.uncertain("HTTP_" + status);
                        return DeliveryResult.failed("HTTP_" + status);
                    });
        } catch (RuntimeException error) {
            for (Throwable cause = error; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConnectException || cause instanceof UnknownHostException || cause instanceof HttpConnectTimeoutException)
                    return DeliveryResult.retry("CONNECT_FAILED", Duration.ZERO);
            }
            // Never persist/log error messages: an HTTP exception may contain the webhook token.
            return DeliveryResult.uncertain("RESPONSE_UNCONFIRMED");
        }
    }
    private Duration retryAfter(String header) {
        try {
            double seconds = Double.parseDouble(header);
            if (!Double.isFinite(seconds) || seconds < 0 || seconds > 86400) return Duration.ZERO;
            return Duration.ofMillis((long) Math.ceil(seconds * 1000));
        } catch (RuntimeException ignored) { return Duration.ZERO; }
    }
}
