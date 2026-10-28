package com.example.dormlaundryalarm.config;

import com.example.dormlaundryalarm.service.WashingMachineService;
import com.example.dormlaundryalarm.service.VibrationTelemetryService;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.context.event.EventListener;
import org.springframework.integration.mqtt.event.MqttConnectionFailedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
@ConditionalOnProperty(name = "mqtt.enabled", havingValue = "true", matchIfMissing = true)
public class MqttConfig {
    private static final Logger log = LoggerFactory.getLogger(MqttConfig.class);
    private final WashingMachineService monitoring;

    public MqttConfig(WashingMachineService monitoring) { this.monitoring = monitoring; }

    @Value("${mqtt.broker-url}")
    private String brokerUrl;

    @Value("${mqtt.client-id}")
    private String clientId;

    @Value("${mqtt.topic}")
    private String topic;

    @Value("${mqtt.telemetry-topic}")
    private String telemetryTopic;

    @Value("${mqtt.status-topic:laundry/status/+}")
    private String statusTopic;

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{brokerUrl});
        options.setAutomaticReconnect(true);
        factory.setConnectionOptions(options);
        return factory;
    }

    @Bean
    public MessageChannel mqttInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageProducer inbound() {
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(
                        clientId, mqttClientFactory(), topic, telemetryTopic, statusTopic);
        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(1);
        adapter.setOutputChannel(mqttInputChannel());
        return adapter;
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttInputChannel")
    public MessageHandler handler(
            WashingMachineService washingMachineService,
            VibrationTelemetryService vibrationTelemetryService) {
        return message -> {
            String receivedTopic = (String) message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC);
            String payload = (String) message.getPayload();

            boolean retained = Boolean.TRUE.equals(message.getHeaders().get(MqttHeaders.RECEIVED_RETAINED));
            if (receivedTopic != null && receivedTopic.startsWith("laundry/status/")) {
                washingMachineService.processPresence(receivedTopic, payload);
            } else if (retained) {
                // Retained historical measurements must never start/finish a new cycle.
                return;
            } else if (receivedTopic != null && receivedTopic.contains("/telemetry/")) {
                vibrationTelemetryService.record(receivedTopic, payload);
            } else {
                washingMachineService.processVibration(receivedTopic, payload);
            }
        };
    }

    @EventListener
    public void onConnectionFailed(MqttConnectionFailedEvent event) {
        try { monitoring.onBrokerDisconnect(); }
        catch (RuntimeException ignored) { log.warn("Could not persist broker disconnect; heartbeat/process guards remain active."); }
    }
}
