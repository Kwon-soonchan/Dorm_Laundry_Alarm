package com.example.dormlaundryalarm.controller;

import com.example.dormlaundryalarm.domain.VibrationTelemetry;
import com.example.dormlaundryalarm.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    private final VibrationTelemetryRepository telemetry;
    private final MachineRepository machines;

    public record Point(Long id, Instant recordedAt, Long sampleCount, Long vibrationCount,
                        Long windowMs, Double vibrationRatio) {}
    public record Snapshot(Instant serverTime, String connection, String machineStatus,
                           Instant lastReceivedAt, long totalSamples, List<Point> points) {}

    @GetMapping
    public Snapshot snapshot(@RequestParam(defaultValue = "1") Long machineId,
                             @RequestParam(defaultValue = "15") int minutes) {
        if (machineId < 1 || minutes < 1 || minutes > 60)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "기기 번호 또는 시간 범위가 잘못되었습니다.");
        Instant now = Instant.now();
        ZoneId zone = ZoneId.systemDefault();
        VibrationTelemetry latest = telemetry.findFirstByMachineIdOrderByRecordedAtDesc(machineId).orElse(null);
        Instant last = latest == null ? null : latest.getRecordedAt().atZone(zone).toInstant();
        String connection = last == null ? "WAITING" :
                Duration.between(last, now).getSeconds() > 15 ? "STALE" : "LIVE";
        List<VibrationTelemetry> rows = telemetry.findByMachineIdAndRecordedAtBetweenOrderByRecordedAtDesc(
                machineId, LocalDateTime.ofInstant(now.minusSeconds(minutes * 60L), zone),
                LocalDateTime.ofInstant(now, zone), PageRequest.of(0, 5000));
        List<Point> points = new ArrayList<>(rows.size());
        for (int i = rows.size() - 1; i >= 0; i--) {
            VibrationTelemetry row = rows.get(i);
            points.add(new Point(row.getId(), row.getRecordedAt().atZone(zone).toInstant(),
                    row.getSampleCount(), row.getVibrationCount(), row.getWindowMs(), row.getVibrationRatio()));
        }
        return new Snapshot(now, connection, machines.findById(machineId).map(m -> m.getStatus()).orElse("UNKNOWN"),
                last, telemetry.countByMachineId(machineId), points);
    }
}
