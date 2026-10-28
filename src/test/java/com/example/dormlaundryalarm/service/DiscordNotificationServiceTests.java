package com.example.dormlaundryalarm.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class DiscordNotificationServiceTests {
    private HttpServer server;
    private ExecutorService executor;
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicReference<String> query = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private int responseCode;
    private String responseBody;
    private String retryAfter;
    private boolean slow;

    @BeforeEach void setup() throws Exception {
        responseCode = 200; responseBody = "{\"id\":\"123456\"}"; retryAfter = null; slow = false;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool(); server.setExecutor(executor);
        server.createContext("/webhook-test", exchange -> {
            requests.incrementAndGet(); query.set(exchange.getRequestURI().getQuery());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            if (slow) { try { Thread.sleep(8000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); } }
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            if (retryAfter != null) exchange.getResponseHeaders().set("Retry-After", retryAfter);
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            try { exchange.sendResponseHeaders(responseCode, responseCode == 204 ? -1 : bytes.length);
                if (responseCode != 204) exchange.getResponseBody().write(bytes);
            } finally { exchange.close(); }
        });
        server.start();
    }
    @AfterEach void close() { server.stop(0); executor.shutdownNow(); }
    private DiscordNotificationService sender() {
        return new DiscordNotificationService("http://127.0.0.1:" + server.getAddress().getPort() + "/webhook-test?wait=false");
    }
    @Test void successfulDeliveryWaitsForReceiptAndUsesStableCycleReference() {
        var result = sender().sendLaundryCompleteNotification(1L, "cycle-reference");
        assertEquals(DeliveryResult.Outcome.SENT, result.outcome());
        assertEquals("123456", result.messageId());
        assertEquals("wait=true", query.get());
        assertTrue(body.get().contains("cycle-reference"));
        assertTrue(body.get().contains("allowed_mentions"));
        assertEquals(1, requests.get());
    }
    @Test void rateLimitResponseRetainsFractionalRetryAfter() {
        responseCode = 429; responseBody = "{\"retry_after\":17.25}"; retryAfter = "17.25";
        var result = sender().sendLaundryCompleteNotification(1L, "cycle");
        assertEquals(DeliveryResult.Outcome.RETRY, result.outcome());
        assertEquals(Duration.ofMillis(17250), result.retryAfter());
    }
    @Test void permanentErrorsAndAmbiguousResultsDoNotBlindlyRetry() {
        responseCode = 404; responseBody = "{}";
        assertEquals(DeliveryResult.Outcome.FAILED, sender().sendLaundryCompleteNotification(1L, "cycle").outcome());
        responseCode = 503;
        assertEquals(DeliveryResult.Outcome.UNCERTAIN, sender().sendLaundryCompleteNotification(1L, "cycle").outcome());
        responseCode = 204; responseBody = "";
        assertEquals(DeliveryResult.Outcome.UNCERTAIN, sender().sendLaundryCompleteNotification(1L, "cycle").outcome());
    }
    @Test void rateLimitCanUseResponseBodyWhenHeaderIsAbsent() {
        responseCode = 429; responseBody = "{\"retry_after\":9.75}"; retryAfter = null;
        var result = sender().sendLaundryCompleteNotification(1L, "cycle");
        assertEquals(Duration.ofMillis(9750), result.retryAfter());
    }
    @Test void invalidWebhookIsPermanentAndNoNetworkRequestIsMade() {
        var result = new DiscordNotificationService("not-a-webhook").sendLaundryCompleteNotification(1L, "cycle");
        assertEquals(DeliveryResult.Outcome.FAILED, result.outcome());
        assertEquals(0, requests.get());
    }
    @Test void unconfirmedResponseTimesOutWithoutResending() {
        slow = true;
        var result = sender().sendLaundryCompleteNotification(1L, "cycle");
        assertEquals(DeliveryResult.Outcome.UNCERTAIN, result.outcome());
        assertEquals(1, requests.get());
    }
    @Test void connectFailureIsRetryableAndErrorCodesContainNoWebhookUrl() throws Exception {
        int unusedPort;
        try (var unused = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) { unusedPort = unused.getLocalPort(); }
        var result = new DiscordNotificationService("http://127.0.0.1:" + unusedPort + "/private-dummy-token")
                .sendLaundryCompleteNotification(1L, "cycle");
        assertEquals(DeliveryResult.Outcome.RETRY, result.outcome());
        assertFalse(result.code().contains("private-dummy-token"));
    }
}
