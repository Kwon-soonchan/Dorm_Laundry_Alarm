package com.example.dormlaundryalarm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

/** Authoritative state keyed by MQTT device ID, independent of generated legacy Machine IDs. */
@Entity
@Getter
@NoArgsConstructor
public class MachineMonitor {
    @Id private Long machineId;
    @Column(nullable = false) private String status;
    private String cycleId;
    private Instant lastVibrationAt;
    private Instant lastMessageAt;
    private Instant quietSince;
    private String processId;
    @Column(nullable = false) private boolean freshWindowRequired;
    private String devicePresence;

    public MachineMonitor(Long machineId, boolean previouslyRunning) {
        this.machineId = machineId;
        this.status = previouslyRunning ? "RUNNING" : "AVAILABLE";
        this.cycleId = previouslyRunning ? UUID.randomUUID().toString() : null;
        this.freshWindowRequired = true;
    }
    public boolean isRunning() { return "RUNNING".equals(status); }
    public void invalidate(String presence) {
        freshWindowRequired = true;
        quietSince = null;
        if (presence != null) devicePresence = presence;
    }
    public void observe(boolean vibrating, Instant now, String currentProcess, long gapSeconds) {
        boolean gap = freshWindowRequired || !currentProcess.equals(processId) || lastMessageAt == null
                || now.isBefore(lastMessageAt) || now.isAfter(lastMessageAt.plusSeconds(gapSeconds));
        if (gap) quietSince = null;
        if (vibrating) {
            if (!isRunning()) {
                cycleId = UUID.randomUUID().toString();
                status = "RUNNING";
            }
            lastVibrationAt = now;
            quietSince = null;
        } else if (isRunning() && quietSince == null) {
            // With continuous delivery, the last observed vibration remains the baseline.
            // After a gap/restart, count only freshly observed silence.
            quietSince = !gap && lastVibrationAt != null ? lastVibrationAt : now;
        }
        lastMessageAt = now;
        processId = currentProcess;
        devicePresence = "online";
        freshWindowRequired = false;
    }
    public boolean canComplete(Instant now, long thresholdSeconds) {
        return isRunning() && quietSince != null && !now.isBefore(quietSince.plusSeconds(thresholdSeconds));
    }
    public void complete() { status = "AVAILABLE"; quietSince = null; }
}
