package com.example.dormlaundryalarm.repository;

import com.example.dormlaundryalarm.domain.MachineMonitor;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface MachineMonitorRepository extends JpaRepository<MachineMonitor, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MachineMonitor m where m.machineId = :id")
    Optional<MachineMonitor> findLocked(@Param("id") Long id);
}
