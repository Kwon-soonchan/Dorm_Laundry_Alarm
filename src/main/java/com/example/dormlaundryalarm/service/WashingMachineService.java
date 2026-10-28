package com.example.dormlaundryalarm.service;

import com.example.dormlaundryalarm.domain.*;
import com.example.dormlaundryalarm.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Clock;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class WashingMachineService {
    private static final Pattern VIBRATION_TOPIC = Pattern.compile("^laundry/vibration/([1-9][0-9]*)$");
    private static final Pattern PRESENCE_TOPIC = Pattern.compile("^laundry/status/([1-9][0-9]*)$");
    private final MachineMonitorRepository monitors;
    private final MachineRepository legacyMachines;
    private final NotificationOutboxRepository outbox;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final long thresholdSeconds;
    private final long gapSeconds;
    private final String processId = UUID.randomUUID().toString();

    public WashingMachineService(MachineMonitorRepository monitors, MachineRepository legacyMachines,
            NotificationOutboxRepository outbox, PlatformTransactionManager manager, Clock clock,
            @Value("${laundry.completion-threshold-seconds:180}") long thresholdSeconds,
            @Value("${laundry.status-gap-seconds:15}") long gapSeconds) {
        if (thresholdSeconds < 1 || gapSeconds < 1) throw new IllegalArgumentException("Invalid monitoring intervals");
        this.monitors = monitors; this.legacyMachines = legacyMachines; this.outbox = outbox;
        this.transactions = new TransactionTemplate(manager); this.clock = clock;
        this.thresholdSeconds = thresholdSeconds; this.gapSeconds = gapSeconds;
    }
    public void processVibration(String topic, String payload) { processVibration(topic, payload, false); }

    public synchronized void processVibration(String topic, String payload, boolean retained) {
        Long machineId = idFrom(VIBRATION_TOPIC, topic);
        if (retained || machineId == null || !("0".equals(payload) || "1".equals(payload))) return;
        // The transaction commits before the monitor lock is released.
        transactions.executeWithoutResult(tx -> {
            MachineMonitor monitor = getOrCreate(machineId);
            var now = clock.instant();
            monitor.observe("1".equals(payload), now, processId, gapSeconds);
            if (monitor.canComplete(now, thresholdSeconds)) {
                outbox.save(new NotificationOutbox(monitor.getCycleId(), machineId, now));
                monitor.complete();
            }
            monitors.save(monitor);
            // Preserve status on existing legacy records; new MQTT IDs live in machine_monitor.
            legacyMachines.findById(machineId).ifPresent(m -> m.updateStatus(monitor.getStatus()));
        });
    }

    public synchronized void processPresence(String topic, String payload) {
        Long machineId = idFrom(PRESENCE_TOPIC, topic);
        if (machineId == null || !("online".equals(payload) || "offline".equals(payload))) return;
        transactions.executeWithoutResult(tx -> {
            MachineMonitor monitor = getOrCreate(machineId);
            monitor.invalidate(payload);
            monitors.save(monitor);
        });
    }

    public synchronized void onBrokerDisconnect() {
        transactions.executeWithoutResult(tx -> {
            for (MachineMonitor monitor : monitors.findAll()) monitor.invalidate("unknown");
        });
    }

    private MachineMonitor getOrCreate(Long id) {
        return monitors.findLocked(id).orElseGet(() -> new MachineMonitor(id,
                legacyMachines.findById(id).map(m -> "RUNNING".equals(m.getStatus())).orElse(false)));
    }
    private Long idFrom(Pattern pattern, String topic) {
        if (topic == null) return null;
        var matcher = pattern.matcher(topic);
        if (!matcher.matches()) return null;
        try { return Long.parseLong(matcher.group(1)); }
        catch (NumberFormatException ignored) { return null; }
    }
}
