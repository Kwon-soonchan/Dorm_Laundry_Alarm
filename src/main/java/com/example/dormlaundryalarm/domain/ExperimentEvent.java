package com.example.dormlaundryalarm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor
public class ExperimentEvent {
    public enum Kind { START, WASH, RINSE, SPIN, FINISH, NOTE }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long experimentId;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private Kind kind;
    @Column(nullable = false, length = 300)
    private String note;
    @Column(nullable = false)
    private Instant recordedAt;

    public ExperimentEvent(Long experimentId, Kind kind, String note) {
        this.experimentId = experimentId;
        this.kind = kind;
        this.note = note;
        this.recordedAt = Instant.now();
    }
}
