package com.example.dormlaundryalarm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor
public class NotificationDispatchGate {
    @Id private String id;
    @Column(nullable = false) private Instant blockedUntil;
    public NotificationDispatchGate(String id) { this.id = id; this.blockedUntil = Instant.EPOCH; }
    public void blockUntil(Instant until) { if (until.isAfter(blockedUntil)) blockedUntil = until; }
}
