package com.example.dormlaundryalarm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "vibration_telemetry",
        indexes = @Index(name = "idx_telemetry_machine_time", columnList = "machineId, recordedAt")
)
@Getter
@NoArgsConstructor
public class VibrationTelemetry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long machineId;

    @Column(nullable = false)
    private LocalDateTime recordedAt;

    @Column(nullable = false)
    private Long windowMs;

    @Column(nullable = false)
    private Long sampleCount;

    @Column(nullable = false)
    private Long vibrationCount;

    @Column(nullable = false)
    private Double vibrationRatio;

    public VibrationTelemetry(
            Long machineId,
            long windowMs,
            long sampleCount,
            long vibrationCount) {
        this.machineId = machineId;
        this.recordedAt = LocalDateTime.now();
        this.windowMs = windowMs;
        this.sampleCount = sampleCount;
        this.vibrationCount = vibrationCount;
        this.vibrationRatio = sampleCount == 0
                ? 0.0
                : (double) vibrationCount / sampleCount;
    }
}
