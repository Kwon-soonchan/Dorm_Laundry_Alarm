package com.example.dormlaundryalarm.service;

import com.example.dormlaundryalarm.domain.NotificationOutbox.Status;
import com.example.dormlaundryalarm.repository.NotificationOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.Clock;

@Service
@RequiredArgsConstructor
public class NotificationDispatcher {
    private final NotificationOutboxRepository repository;
    private final NotificationOutboxService outbox;
    private final NotificationService sender;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${notification.poll-ms:2000}")
    public void dispatch() {
        outbox.recoverAbandonedClaims();
        // Keep queued events intact when the webhook is not configured.
        if (!sender.isConfigured()) return;
        for (var due : repository.findTop10ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(Status.PENDING, clock.instant())) {
            var claim = outbox.claim(due.getId());
            if (claim == null) continue;
            DeliveryResult result;
            try { result = sender.sendLaundryCompleteNotification(claim.machineId(), claim.id()); }
            catch (RuntimeException ignored) { result = DeliveryResult.uncertain("UNEXPECTED_TRANSPORT_ERROR"); }
            outbox.finish(claim, result);
        }
    }
}
