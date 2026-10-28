package com.example.dormlaundryalarm.controller;

import com.example.dormlaundryalarm.domain.VibrationTelemetry;
import com.example.dormlaundryalarm.repository.VibrationTelemetryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/telemetry")
@RequiredArgsConstructor
public class TelemetryExportController {

    private final VibrationTelemetryRepository repository;

    @GetMapping(value = "/export.csv", produces = "text/csv")
    public ResponseEntity<byte[]> exportCsv(@RequestParam(required = false) Long machineId) {
        StringBuilder csv = new StringBuilder(
                "id,machine_id,recorded_at,window_ms,sample_count,vibration_count,vibration_ratio\n");

        for (VibrationTelemetry row : machineId == null ? repository.findAllByOrderByRecordedAtAsc()
                : repository.findByMachineIdOrderByRecordedAtAsc(machineId)) {
            csv.append(row.getId()).append(',')
                    .append(row.getMachineId()).append(',')
                    .append(row.getRecordedAt()).append(',')
                    .append(row.getWindowMs()).append(',')
                    .append(row.getSampleCount()).append(',')
                    .append(row.getVibrationCount()).append(',')
                    .append(row.getVibrationRatio()).append('\n');
        }

        byte[] body = csv.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=vibration-telemetry.csv")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(body);
    }
}
