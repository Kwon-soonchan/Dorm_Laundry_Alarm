// src/main/java/com/example/dormlaundryalarm/domain/UsageLog.java
package com.example.dormlaundryalarm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
public class UsageLog {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "machine_id")
    private Machine machine;

    private Long userId; // 당장은 MVP이므로 임시 사용자 ID 사용

    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    private Boolean isCollected; // 수거 여부

    public UsageLog(Machine machine, Long userId) {
        this.machine = machine;
        this.userId = userId;
        this.startedAt = LocalDateTime.now();
        this.isCollected = false;
    }

    // 비즈니스 로직: 세탁 완료 처리
    public void markAsFinished() {
        this.finishedAt = LocalDateTime.now();
    }
}