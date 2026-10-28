#include <ESP8266WiFi.h>
#include <PubSubClient.h>
#include "secrets.h"

const char* ssid = WIFI_SSID;
const char* password = WIFI_PASSWORD;
const char* mqttServer = MQTT_SERVER;

WiFiClient espClient;
PubSubClient client(espClient);

const int SENSOR_PIN = D1;
int lastState = -1;
unsigned long lastMessageTime = 0;
unsigned long lastWifiAttemptTime = 0;
unsigned long lastMqttAttemptTime = 0;
unsigned long lastSensorSampleTime = 0;
unsigned long telemetryWindowStartTime = 0;
unsigned long telemetrySampleCount = 0;
unsigned long telemetryVibrationCount = 0;

const unsigned long WIFI_RETRY_INTERVAL_MS = 5000;
const unsigned long MQTT_RETRY_INTERVAL_MS = 5000;
const unsigned long SENSOR_SAMPLE_INTERVAL_MS = 10;
const unsigned long TELEMETRY_WINDOW_MS = 1000;
const char* STATUS_TOPIC = "laundry/status/1";

void setupWifi() {
  WiFi.mode(WIFI_STA);
  WiFi.begin(ssid, password);

  Serial.print("Connecting to Wi-Fi: ");
  Serial.println(ssid);
}

void reconnectMqtt() {
  String clientId = "LaundryClient_1-" + String(ESP.getChipId(), HEX);

  Serial.print("Connecting to MQTT broker ");
  Serial.print(mqttServer);
  Serial.print(":1883 ... ");

  if (client.connect(clientId.c_str(), STATUS_TOPIC, 1, true, "offline")) {
    Serial.println("connected");
    client.publish(STATUS_TOPIC, "online", true);
    // Disconnected time is not a measured telemetry window. Never backfill it as silence.
    telemetrySampleCount = 0;
    telemetryVibrationCount = 0;
    telemetryWindowStartTime = millis();
    lastSensorSampleTime = millis();
    lastState = -1;
  } else {
    Serial.print("failed, state=");
    Serial.println(client.state());
  }
}

void setup() {
  pinMode(SENSOR_PIN, INPUT);
  Serial.begin(115200);
  delay(500);
  Serial.println();
  Serial.println("Laundry sensor starting");
  setupWifi();
  client.setServer(mqttServer, 1883);
  client.setKeepAlive(10);
  client.setSocketTimeout(5);
  telemetryWindowStartTime = millis();
}

void loop() {
  unsigned long now = millis();

  if (WiFi.status() != WL_CONNECTED) {
    if (now - lastWifiAttemptTime >= WIFI_RETRY_INTERVAL_MS) {
      lastWifiAttemptTime = now;
      Serial.println("Wi-Fi disconnected; reconnecting...");
      WiFi.disconnect();
      WiFi.begin(ssid, password);
    }
    delay(10);
    return;
  }

  if (!client.connected()) {
    if (now - lastMqttAttemptTime >= MQTT_RETRY_INTERVAL_MS) {
      lastMqttAttemptTime = now;
      reconnectMqtt();
    }
    delay(10);
    return;
  }

  client.loop();
  int currentState = digitalRead(SENSOR_PIN);

  if (now - lastSensorSampleTime >= SENSOR_SAMPLE_INTERVAL_MS) {
    lastSensorSampleTime = now;
    telemetrySampleCount++;
    if (currentState == HIGH) {
      telemetryVibrationCount++;
    }
  }

  if (currentState != lastState || now - lastMessageTime > 5000) {
    lastState = currentState;
    lastMessageTime = now;

    String payload = String(currentState);
    if (!client.publish("laundry/vibration/1", payload.c_str())) {
      espClient.stop();
      return;
    }

    Serial.print("Vibration state: ");
    Serial.println(payload);
  }

  if (now - telemetryWindowStartTime >= TELEMETRY_WINDOW_MS && telemetrySampleCount > 0) {
    unsigned long actualWindowMs = now - telemetryWindowStartTime;
    char telemetryPayload[64];
    snprintf(
      telemetryPayload,
      sizeof(telemetryPayload),
      "%lu,%lu,%lu",
      telemetrySampleCount,
      telemetryVibrationCount,
      actualWindowMs
    );

    if (!client.publish("laundry/telemetry/1", telemetryPayload)) {
      espClient.stop();
      return;
    }

    Serial.print("Telemetry samples,hits,windowMs: ");
    Serial.println(telemetryPayload);

    telemetrySampleCount = 0;
    telemetryVibrationCount = 0;
    telemetryWindowStartTime = now;
  }
}
