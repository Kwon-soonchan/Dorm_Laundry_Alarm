package com.example.dormlaundryalarm.repository;

import com.example.dormlaundryalarm.domain.ExperimentEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExperimentEventRepository extends JpaRepository<ExperimentEvent, Long> {
    List<ExperimentEvent> findByExperimentIdOrderByRecordedAtAsc(Long experimentId);
}
