package com.example.dormlaundryalarm.repository;

import com.example.dormlaundryalarm.domain.NotificationOutbox;
import com.example.dormlaundryalarm.domain.NotificationOutbox.Status;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from NotificationOutbox n where n.id = :id")
    Optional<NotificationOutbox> findLocked(@Param("id") String id);
    List<NotificationOutbox> findTop10ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(Status status, Instant due);
    List<NotificationOutbox> findTop100ByStatusAndLastAttemptAtBefore(Status status, Instant cutoff);
    List<NotificationOutbox> findTop50ByMachineIdOrderByCreatedAtDesc(Long machineId);
}
