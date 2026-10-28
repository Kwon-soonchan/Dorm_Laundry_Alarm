package com.example.dormlaundryalarm;

import com.example.dormlaundryalarm.repository.*;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

/** Optional real-broker test. Uses its own loopback-only broker, no device/production DB/webhook. */
@EnabledIfSystemProperty(named = "laundry.mqtt.smoke", matches = "true")
@ActiveProfiles("test")
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:mqtt_smoke;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "mqtt.enabled=true", "mqtt.client-id=laundry-reliability-test",
        "laundry.completion-threshold-seconds=10", "laundry.status-gap-seconds=15"})
@Import(ReliabilityTests.Fixtures.class)
class MqttBrokerIntegrationTests {
    static Process broker;
    static int port;
    static String executable;
    @Autowired MachineMonitorRepository monitors;
    @Autowired NotificationOutboxRepository notifications;
    @Autowired ReliabilityTests.MutableClock clock;

    @DynamicPropertySource static void brokerProperties(DynamicPropertyRegistry registry) throws Exception {
        executable = System.getenv().getOrDefault("MOSQUITTO_EXECUTABLE", "C:/Program Files/mosquitto/mosquitto.exe");
        try (var socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) { port = socket.getLocalPort(); }
        startBroker();
        registry.add("mqtt.broker-url", () -> "tcp://127.0.0.1:" + port);
    }
    static void startBroker() throws Exception {
        broker = new ProcessBuilder(executable, "-p", Integer.toString(port))
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        await(() -> {
            try (var socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", port), 200); return true;
            } catch (Exception ignored) { return false; }
        }, 5000);
    }
    static void await(BooleanSupplier condition, long timeoutMs) throws Exception {
        long deadline = System.nanoTime() + Duration.ofMillis(timeoutMs).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) fail("Timed out waiting for MQTT observation");
            Thread.sleep(50);
        }
    }
    MqttClient publisher() throws Exception {
        var client = new MqttClient("tcp://127.0.0.1:" + port, MqttClient.generateClientId(), new MemoryPersistence());
        client.connect(); return client;
    }
    void publish(MqttClient client, String topic, String value, boolean retained) throws Exception {
        var message = new MqttMessage(value.getBytes(StandardCharsets.UTF_8));
        message.setQos(1); message.setRetained(retained);
        client.publish(topic, message);
    }
    void status(MqttClient client, String value) throws Exception {
        publish(client, "laundry/vibration/1", value, false);
        await(() -> monitors.findById(1L).map(m -> clock.instant().equals(m.getLastMessageAt())).orElse(false), 5000);
    }
    @Test void actualBrokerRestartRequiresFreshObservationBeforeQueuingCompletion() throws Exception {
        MqttClient first = publisher();
        try {
            publish(first, "laundry/status/1", "online", true);
            await(() -> monitors.findById(1L).isPresent(), 10000);
            status(first, "1");
            clock.advance(5); status(first, "0");
            String cycle = monitors.findById(1L).orElseThrow().getCycleId();
            broker.destroyForcibly(); assertTrue(broker.waitFor(5, java.util.concurrent.TimeUnit.SECONDS));
            await(() -> monitors.findById(1L).orElseThrow().isFreshWindowRequired(), 10000);
            clock.advance(60);
            startBroker();
            try (MqttClient second = publisher()) {
                // Retain presence until the backend has re-subscribed.
                publish(second, "laundry/status/1", "offline", true);
                await(() -> "offline".equals(monitors.findById(1L).orElseThrow().getDevicePresence()), 25000);
                status(second, "0");
                assertEquals(0, notifications.count());
                assertEquals(cycle, monitors.findById(1L).orElseThrow().getCycleId());
                clock.advance(5); status(second, "0");
                clock.advance(5); status(second, "0");
                await(() -> notifications.count() == 1, 5000);
                assertEquals(cycle, notifications.findAll().getFirst().getId());
                clock.advance(5); status(second, "0");
                assertEquals(1, notifications.count());
                second.disconnect();
            }
        } finally {
            if (first.isConnected()) first.disconnect();
            first.close(true);
        }
    }
    @AfterAll static void stopBroker() throws Exception {
        if (broker != null) { broker.destroyForcibly(); broker.waitFor(5, java.util.concurrent.TimeUnit.SECONDS); }
    }
}
