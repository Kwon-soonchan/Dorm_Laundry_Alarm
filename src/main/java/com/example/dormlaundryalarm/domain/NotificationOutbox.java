package com.example.dormlaundryalarm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(indexes = @Index(name = "idx_outbox_due", columnList = "status,nextAttemptAt"))
@Getter
@NoArgsConstructor
public class NotificationOutbox {
    public enum Status { PENDING, SENDING, SENT, FAILED, UNCERTAIN }
    // A single durable event per washing cycle. MQTT redelivery cannot enqueue it twice.
    @Id @Column(length = 36) private String id;
    @Column(nullable = false) private Long machineId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status;
    @Column(nullable = false) private int attempts;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant nextAttemptAt;
    private Instant lastAttemptAt;
    private Instant sentAt;
    private String leaseToken;
    private String lastErrorCode;
    private String discordMessageId;

    public NotificationOutbox(String cycleId, Long machineId, Instant now) {
        this.id = cycleId;
        this.machineId = machineId;
        this.status = Status.PENDING;
        this.createdAt = now;
        this.nextAttemptAt = now;
    }
    public String claim(Instant now) {
        status = Status.SENDING;
        attempts++;
        lastAttemptAt = now;
        leaseToken = UUID.randomUUID().toString();
        return leaseToken;
    }
    public void sent(Instant now, String receipt) {
        status = Status.SENT; sentAt = now; discordMessageId = receipt; lastErrorCode = null;
    }
    public void retry(Instant when, String code) { status = Status.PENDING; nextAttemptAt = when; lastErrorCode = code; }
    public void fail(String code) { status = Status.FAILED; lastErrorCode = code; }
    public void uncertain(String code) { status = Status.UNCERTAIN; lastErrorCode = code; }
    public void manualRetry(Instant now) { status = Status.PENDING; nextAttemptAt = now; attempts = 0; leaseToken = null; lastErrorCode = "MANUAL_RETRY"; }
}
