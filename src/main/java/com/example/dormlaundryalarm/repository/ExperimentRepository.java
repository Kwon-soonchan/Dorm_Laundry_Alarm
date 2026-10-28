package com.example.dormlaundryalarm.repository;

import com.example.dormlaundryalarm.domain.Experiment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExperimentRepository extends JpaRepository<Experiment, Long> {
    List<Experiment> findTop50ByMachineIdOrderByStartedAtDesc(Long machineId);
    boolean existsByMachineIdAndEndedAtIsNull(Long machineId);
}
