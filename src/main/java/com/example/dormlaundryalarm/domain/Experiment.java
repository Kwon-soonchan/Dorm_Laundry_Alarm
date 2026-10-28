package com.example.dormlaundryalarm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor
public class Experiment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 80)
    private String title;
    @Column(nullable = false)
    private Long machineId;
    @Column(nullable = false)
    private Instant startedAt;
    private Instant endedAt;

    public Experiment(String title, Long machineId) {
        this.title = title;
        this.machineId = machineId;
        this.startedAt = Instant.now();
    }
    public void finish() { this.endedAt = Instant.now(); }
}
