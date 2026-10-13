package com.example.dormlaundryalarm.repository;

import com.example.dormlaundryalarm.domain.VibrationTelemetry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VibrationTelemetryRepository extends JpaRepository<VibrationTelemetry, Long> {
    List<VibrationTelemetry> findAllByOrderByRecordedAtAsc();
}
