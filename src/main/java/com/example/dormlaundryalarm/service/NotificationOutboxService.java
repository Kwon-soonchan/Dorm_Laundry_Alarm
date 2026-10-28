package com.example.dormlaundryalarm.service;

import com.example.dormlaundryalarm.domain.NotificationOutbox;
import com.example.dormlaundryalarm.domain.NotificationOutbox.Status;
import com.example.dormlaundryalarm.repository.NotificationOutboxRepository;
import com.example.dormlaundryalarm.repository.NotificationDispatchGateRepository;
import com.example.dormlaundryalarm.domain.NotificationDispatchGate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;

@Service
@RequiredArgsConstructor
public class NotificationOutboxService {
    private final NotificationOutboxRepository repository;
    private final Clock clock;
    private final NotificationDispatchGateRepository gates;
    @Value("${notification.max-attempts:8}") private int maxAttempts;
    @Value("${notification.retry-base-seconds:5}") private long retryBaseSeconds;
    @Value("${notification.retry-max-seconds:300}") private long retryMaxSeconds;
    @Value("${notification.lease-seconds:60}") private long leaseSeconds;
    public record Claim(String id, Long machineId, String token) {}

    @Transactional
    public Claim claim(String id) {
        NotificationOutbox row = repository.findLocked(id).orElse(null);
        Instant now = clock.instant();
        if (gates.findById("discord").map(g -> g.getBlockedUntil().isAfter(now)).orElse(false)) return null;
        if (row == null || row.getStatus() != Status.PENDING || row.getNextAttemptAt().isAfter(now)) return null;
        if (row.getAttempts() >= maxAttempts) { row.fail("RETRY_EXHAUSTED"); return null; }
        return new Claim(row.getId(), row.getMachineId(), row.claim(now));
    }

    @Transactional
    public void finish(Claim claim, DeliveryResult result) {
        NotificationOutbox row = repository.findLocked(claim.id()).orElseThrow();
        if (!claim.token().equals(row.getLeaseToken()) ||
                row.getStatus() != Status.SENDING && row.getStatus() != Status.UNCERTAIN) return;
        switch (result.outcome()) {
            case SENT -> row.sent(clock.instant(), result.messageId());
            case FAILED -> row.fail(result.code());
            case UNCERTAIN -> row.uncertain(result.code());
            case RETRY -> {
                long backoff = Math.min(retryMaxSeconds,
                        retryBaseSeconds * (1L << Math.min(row.getAttempts() - 1, 20)));
                Duration wait = Duration.ofSeconds(Math.max(1, backoff));
                if (result.retryAfter().compareTo(wait) > 0) wait = result.retryAfter();
                if ("HTTP_429".equals(result.code())) {
                    NotificationDispatchGate gate = gates.findById("discord").orElseGet(() -> new NotificationDispatchGate("discord"));
                    gate.blockUntil(clock.instant().plus(wait));
                    gates.save(gate);
                }
                if (row.getAttempts() >= maxAttempts) row.fail("RETRY_EXHAUSTED_" + result.code());
                else row.retry(clock.instant().plus(wait), result.code());
            }
        }
    }

    @Transactional
    public void recoverAbandonedClaims() {
        for (NotificationOutbox candidate : repository.findTop100ByStatusAndLastAttemptAtBefore(
                Status.SENDING, clock.instant().minusSeconds(leaseSeconds))) {
            NotificationOutbox row = repository.findLocked(candidate.getId()).orElseThrow();
            if (row.getStatus() == Status.SENDING && row.getLastAttemptAt().isBefore(clock.instant().minusSeconds(leaseSeconds)))
                row.uncertain("PROCESS_INTERRUPTED_DURING_SEND");
        }
    }

    @Transactional
    public void manualRetry(String id, boolean confirmedNoDelivery) {
        NotificationOutbox row = repository.findLocked(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!confirmedNoDelivery) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Discord에서 미수신을 확인해야 합니다.");
        if (row.getStatus() != Status.FAILED && row.getStatus() != Status.UNCERTAIN)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "실패 또는 확인 필요 알림만 재시도할 수 있습니다.");
        row.manualRetry(clock.instant());
    }
}
