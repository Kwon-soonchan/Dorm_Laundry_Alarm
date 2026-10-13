package com.example.dormlaundryalarm.service;

import com.example.dormlaundryalarm.domain.VibrationTelemetry;
import com.example.dormlaundryalarm.repository.VibrationTelemetryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VibrationTelemetryService {

    private final VibrationTelemetryRepository repository;

    @Transactional
    public void record(String topic, String payload) {
        try {
            long machineId = parseMachineId(topic);
            String[] values = payload.split(",");
            if (values.length != 3) {
                throw new IllegalArgumentException("expected samples,hits,windowMs");
            }

            long sampleCount = Long.parseLong(values[0].trim());
            long vibrationCount = Long.parseLong(values[1].trim());
            long windowMs = Long.parseLong(values[2].trim());

            if (sampleCount <= 0 || vibrationCount < 0 || vibrationCount > sampleCount || windowMs <= 0) {
                throw new IllegalArgumentException("telemetry values are out of range");
            }

            repository.save(new VibrationTelemetry(
                    machineId, windowMs, sampleCount, vibrationCount));
        } catch (RuntimeException exception) {
            System.err.println("Invalid telemetry message [" + topic + "]: " + exception.getMessage());
        }
    }

    private long parseMachineId(String topic) {
        String[] parts = topic.split("/");
        return Long.parseLong(parts[parts.length - 1]);
    }
}
