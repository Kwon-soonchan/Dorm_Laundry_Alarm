package com.example.dormlaundryalarm;

import com.example.dormlaundryalarm.domain.NotificationOutbox.Status;
import com.example.dormlaundryalarm.repository.*;
import com.example.dormlaundryalarm.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ActiveProfiles("test")
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:reliability;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "laundry.completion-threshold-seconds=10", "laundry.status-gap-seconds=15", "notification.max-attempts=3"})
@Import(ReliabilityTests.Fixtures.class)
class ReliabilityTests {
    static class MutableClock extends Clock {
        volatile Instant current = Instant.parse("2026-01-01T00:00:00Z");
        void advance(long seconds) { current = current.plusSeconds(seconds); }
        public Instant instant() { return current; }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
    }
    static class FakeSender implements NotificationService {
        boolean configured = true;
        List<String> calls = new ArrayList<>();
        Deque<DeliveryResult> results = new ArrayDeque<>();
        public boolean isConfigured() { return configured; }
        public DeliveryResult sendLaundryCompleteNotification(Long machineId, String cycleId) {
            calls.add(cycleId);
            return results.isEmpty() ? DeliveryResult.sent("receipt-" + cycleId) : results.removeFirst();
        }
    }
    @TestConfiguration
    static class Fixtures {
        @Bean @Primary MutableClock testClock() { return new MutableClock(); }
        @Bean @Primary FakeSender testSender() { return new FakeSender(); }
    }
    @Autowired MutableClock clock;
    @Autowired FakeSender sender;
    @Autowired WashingMachineService washing;
    @Autowired MachineMonitorRepository monitors;
    @Autowired MachineRepository legacy;
    @Autowired NotificationOutboxRepository notifications;
    @Autowired NotificationDispatchGateRepository gates;
    @Autowired NotificationOutboxService outbox;
    @Autowired NotificationDispatcher dispatcher;
    @Autowired PlatformTransactionManager transactions;

    @BeforeEach void reset() {
        notifications.deleteAll(); gates.deleteAll(); monitors.deleteAll(); legacy.deleteAll();
        clock.current = Instant.parse("2026-01-01T00:00:00Z");
        sender.calls.clear(); sender.results.clear(); sender.configured = true;
    }
    private void status(String value) { washing.processVibration("laundry/vibration/1", value); }
    private void completeCycle() {
        status("1"); clock.advance(5); status("0"); clock.advance(5); status("0");
    }
    private WashingMachineService newProcess(NotificationOutboxRepository repository) {
        return new WashingMachineService(monitors, legacy, repository, transactions, clock, 10, 15);
    }

    @Test void continuousSilenceUsesExistingThresholdAndQueuesOnlyOnce() throws Exception {
        completeCycle();
        assertEquals("AVAILABLE", monitors.findById(1L).orElseThrow().getStatus());
        assertEquals(1, notifications.count());
        try (var pool = Executors.newFixedThreadPool(4)) {
            List<Future<?>> tasks = new ArrayList<>();
            for (int i = 0; i < 8; i++) tasks.add(pool.submit(() -> status("0")));
            for (var task : tasks) task.get(10, TimeUnit.SECONDS);
        }
        assertEquals(1, notifications.count());
        dispatcher.dispatch(); dispatcher.dispatch();
        assertEquals(1, sender.calls.size());
        assertEquals(Status.SENT, notifications.findAll().getFirst().getStatus());
    }

    @Test void restartPreservesCycleAndRequiresFreshSilenceBeforeCompletion() {
        status("1"); clock.advance(5); status("0");
        String cycleId = monitors.findById(1L).orElseThrow().getCycleId();
        var restarted = newProcess(notifications);
        clock.advance(5); restarted.processVibration("laundry/vibration/1", "0");
        assertEquals(0, notifications.count());
        assertEquals(cycleId, monitors.findById(1L).orElseThrow().getCycleId());
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), monitors.findById(1L).orElseThrow().getLastVibrationAt());
        clock.advance(5); restarted.processVibration("laundry/vibration/1", "0");
        clock.advance(5); restarted.processVibration("laundry/vibration/1", "0");
        assertEquals(cycleId, notifications.findAll().getFirst().getId());
        newProcess(notifications).processVibration("laundry/vibration/1", "0");
        assertEquals(1, notifications.count());
    }

    @Test void heartbeatGapAndDeviceOrBrokerDisconnectNeverCountAsSilence() {
        status("1"); clock.advance(5); status("0");
        clock.advance(60); status("0");
        assertEquals(0, notifications.count());
        clock.advance(5); status("0");
        washing.processPresence("laundry/status/1", "offline");
        assertTrue(monitors.findById(1L).orElseThrow().isFreshWindowRequired());
        washing.processPresence("laundry/status/1", "online");
        clock.advance(5); status("0");
        assertEquals(0, notifications.count());
        washing.onBrokerDisconnect();
        clock.advance(5); status("0");
        assertEquals(0, notifications.count());
        clock.advance(5); status("0"); clock.advance(5); status("0");
        assertEquals(1, notifications.count());
    }

    @Test void oldRetainedAndInvalidMessagesCannotCreateOrCompleteCycles() {
        washing.processVibration("laundry/vibration/1", "1", true);
        washing.processVibration(null, "1");
        washing.processVibration("laundry/vibration/0", "1");
        washing.processVibration("laundry/vibration/1", "bad");
        washing.processVibration("laundry/vibration/99999999999999999999999999", "1");
        assertEquals(0, monitors.count());
        washing.processVibration("laundry/vibration/27", "1");
        assertTrue(monitors.findById(27L).orElseThrow().isRunning());
        assertTrue(monitors.findById(1L).isEmpty());
    }

    @Test void failedQueueWriteRollsBackCompletionStatus() {
        var broken = mock(NotificationOutboxRepository.class);
        when(broken.save(any())).thenThrow(new IllegalStateException("simulated DB failure"));
        var service = newProcess(broken);
        service.processVibration("laundry/vibration/1", "1");
        clock.advance(5); service.processVibration("laundry/vibration/1", "0");
        clock.advance(5);
        assertThrows(IllegalStateException.class, () -> service.processVibration("laundry/vibration/1", "0"));
        assertTrue(monitors.findById(1L).orElseThrow().isRunning());
        assertEquals(0, notifications.count());
    }

    @Test void retryQueueSurvivesWorkerRestartAndRespectsRateLimitDelay() {
        completeCycle();
        sender.results.add(DeliveryResult.retry("HTTP_429", Duration.ofSeconds(17)));
        dispatcher.dispatch();
        var row = notifications.findAll().getFirst();
        assertEquals(Status.PENDING, row.getStatus());
        assertEquals(clock.instant().plusSeconds(17), row.getNextAttemptAt());
        var restarted = new NotificationDispatcher(notifications, outbox, sender, clock);
        clock.advance(16); restarted.dispatch(); assertEquals(1, sender.calls.size());
        clock.advance(1); restarted.dispatch(); assertEquals(2, sender.calls.size());
        assertEquals(sender.calls.getFirst(), sender.calls.getLast());
        assertNotNull(notifications.findById(row.getId()).orElseThrow().getDiscordMessageId());
        restarted.dispatch(); assertEquals(2, sender.calls.size());
    }

    @Test void unconfiguredWebhookDoesNotDiscardQueuedNotifications() {
        completeCycle(); sender.configured = false; dispatcher.dispatch();
        var row = notifications.findAll().getFirst();
        assertEquals(Status.PENDING, row.getStatus()); assertEquals(0, row.getAttempts());
        sender.configured = true; dispatcher.dispatch();
        assertEquals(Status.SENT, notifications.findById(row.getId()).orElseThrow().getStatus());
    }

    @Test void lastRateLimitedAttemptStillBlocksOtherQueuedEvents() {
        completeCycle();
        sender.results.add(DeliveryResult.retry("CONNECT_FAILED", Duration.ZERO));
        dispatcher.dispatch(); clock.advance(5);
        sender.results.add(DeliveryResult.retry("CONNECT_FAILED", Duration.ZERO));
        dispatcher.dispatch(); clock.advance(10);
        notifications.save(new com.example.dormlaundryalarm.domain.NotificationOutbox(
                java.util.UUID.randomUUID().toString(), 2L, clock.instant()));
        sender.results.add(DeliveryResult.retry("HTTP_429", Duration.ofSeconds(17)));
        dispatcher.dispatch();
        assertEquals(3, sender.calls.size());
        assertTrue(notifications.findAll().stream().anyMatch(n -> n.getStatus() == Status.FAILED));
        clock.advance(20); dispatcher.dispatch();
        assertEquals(4, sender.calls.size());
    }

    @Test void rateLimitPausesAllEventsAcrossDispatcherRestart() {
        completeCycle();
        washing.processVibration("laundry/vibration/2", "1");
        clock.advance(5); washing.processVibration("laundry/vibration/2", "0");
        clock.advance(5); washing.processVibration("laundry/vibration/2", "0");
        sender.results.add(DeliveryResult.retry("HTTP_429", Duration.ofSeconds(30)));
        dispatcher.dispatch();
        assertEquals(1, sender.calls.size());
        var restarted = new NotificationDispatcher(notifications, outbox, sender, clock);
        clock.advance(29); restarted.dispatch(); assertEquals(1, sender.calls.size());
        clock.advance(1); restarted.dispatch(); assertEquals(3, sender.calls.size());
    }

    @Test void retryBudgetAndUncertainSendRequireExplicitManualReview() {
        completeCycle();
        for (int i = 0; i < 3; i++) sender.results.add(DeliveryResult.retry("CONNECT_FAILED", Duration.ZERO));
        for (int i = 0; i < 3; i++) { dispatcher.dispatch(); clock.advance(1000); }
        var row = notifications.findAll().getFirst();
        assertEquals(Status.FAILED, row.getStatus()); assertEquals(3, row.getAttempts());
        dispatcher.dispatch(); assertEquals(3, sender.calls.size());
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> outbox.manualRetry(row.getId(), false));
        outbox.manualRetry(row.getId(), true);
        sender.results.add(DeliveryResult.uncertain("RESPONSE_UNCONFIRMED"));
        dispatcher.dispatch();
        assertEquals(Status.UNCERTAIN, notifications.findById(row.getId()).orElseThrow().getStatus());
        clock.advance(1000); dispatcher.dispatch(); assertEquals(4, sender.calls.size());
        outbox.manualRetry(row.getId(), true); dispatcher.dispatch();
        assertEquals(Status.SENT, notifications.findById(row.getId()).orElseThrow().getStatus());
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> outbox.manualRetry(row.getId(), true));
    }

    @Test void processCrashDuringDeliveryIsNotAutomaticallyResent() {
        completeCycle(); String id = notifications.findAll().getFirst().getId();
        var claim = outbox.claim(id);
        assertNotNull(claim); assertNull(outbox.claim(id));
        clock.advance(61); dispatcher.dispatch();
        assertEquals(Status.UNCERTAIN, notifications.findById(id).orElseThrow().getStatus());
        assertEquals(0, sender.calls.size());
        // A late confirmed receipt from the original lease may still close the event safely.
        outbox.finish(claim, DeliveryResult.sent("late-confirmation"));
        assertEquals(Status.SENT, notifications.findById(id).orElseThrow().getStatus());
        dispatcher.dispatch(); assertEquals(0, sender.calls.size());
    }
}
