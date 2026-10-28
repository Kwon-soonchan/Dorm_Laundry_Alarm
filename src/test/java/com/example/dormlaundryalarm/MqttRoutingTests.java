package com.example.dormlaundryalarm;

import com.example.dormlaundryalarm.config.MqttConfig;
import com.example.dormlaundryalarm.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.support.MessageBuilder;
import static org.mockito.Mockito.*;

class MqttRoutingTests {
    @Test void retainedMeasurementsAreIgnoredButRetainedPresenceInvalidatesObservationWindow() {
        var washing = mock(WashingMachineService.class);
        var telemetry = mock(VibrationTelemetryService.class);
        var handler = new MqttConfig(washing).handler(washing, telemetry);
        handler.handleMessage(MessageBuilder.withPayload("1")
                .setHeader(MqttHeaders.RECEIVED_TOPIC, "laundry/vibration/1")
                .setHeader(MqttHeaders.RECEIVED_RETAINED, true).build());
        handler.handleMessage(MessageBuilder.withPayload("100,10,1000")
                .setHeader(MqttHeaders.RECEIVED_TOPIC, "laundry/telemetry/1")
                .setHeader(MqttHeaders.RECEIVED_RETAINED, true).build());
        verifyNoInteractions(washing, telemetry);
        handler.handleMessage(MessageBuilder.withPayload("online")
                .setHeader(MqttHeaders.RECEIVED_TOPIC, "laundry/status/1")
                .setHeader(MqttHeaders.RECEIVED_RETAINED, true).build());
        verify(washing).processPresence("laundry/status/1", "online");
    }
}
