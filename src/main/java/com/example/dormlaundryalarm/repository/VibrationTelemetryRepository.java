package com.example.dormlaundryalarm.repository;

import com.example.dormlaundryalarm.domain.VibrationTelemetry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import org.springframework.data.domain.Pageable;

public interface VibrationTelemetryRepository extends JpaRepository<VibrationTelemetry, Long> {
    List<VibrationTelemetry> findAllByOrderByRecordedAtAsc();
    List<VibrationTelemetry> findByMachineIdOrderByRecordedAtAsc(Long machineId);
    Optional<VibrationTelemetry> findFirstByMachineIdOrderByRecordedAtDesc(Long machineId);
    long countByMachineId(Long machineId);
    List<VibrationTelemetry> findByMachineIdAndRecordedAtBetweenOrderByRecordedAtDesc(
            Long machineId, LocalDateTime from, LocalDateTime to, Pageable pageable);
    List<VibrationTelemetry> findByMachineIdAndRecordedAtBetweenOrderByRecordedAtAsc(
            Long machineId, LocalDateTime from, LocalDateTime to);
}
