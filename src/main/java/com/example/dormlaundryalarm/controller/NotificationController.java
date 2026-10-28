package com.example.dormlaundryalarm.controller;

import com.example.dormlaundryalarm.domain.NotificationOutbox;
import com.example.dormlaundryalarm.repository.NotificationOutboxRepository;
import com.example.dormlaundryalarm.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.List;
import java.time.Instant;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationOutboxRepository repository;
    private final NotificationOutboxService outbox;
    private final NotificationService sender;
    public record NotificationView(String id, Long machineId, NotificationOutbox.Status status, int attempts,
                                   Instant createdAt, Instant nextAttemptAt, Instant lastAttemptAt,
                                   Instant sentAt, String lastErrorCode, String discordMessageId) {
        static NotificationView of(NotificationOutbox row) {
            return new NotificationView(row.getId(), row.getMachineId(), row.getStatus(), row.getAttempts(),
                    row.getCreatedAt(), row.getNextAttemptAt(), row.getLastAttemptAt(), row.getSentAt(),
                    row.getLastErrorCode(), row.getDiscordMessageId());
        }
    }
    public record Notifications(boolean configured, List<NotificationView> items) {}
    public record RetryRequest(boolean confirmedNoDelivery) {}

    @GetMapping
    public Notifications list(@RequestParam(defaultValue = "1") Long machineId) {
        if (machineId < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        return new Notifications(sender.isConfigured(), repository.findTop50ByMachineIdOrderByCreatedAtDesc(machineId)
                .stream().map(NotificationView::of).toList());
    }
    @PostMapping("/{id}/retry")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retry(@PathVariable String id, @RequestBody RetryRequest request) {
        outbox.manualRetry(id, request.confirmedNoDelivery());
    }
}
