const int SENSOR_PIN = D1;

int lastState = -1;
unsigned long lastReportTime = 0;

void setup() {
  pinMode(SENSOR_PIN, INPUT);
  Serial.begin(115200);
  delay(500);
  Serial.println();
  Serial.println("SW-420 serial test started");
  Serial.println("Shake the sensor and watch for HIGH/LOW changes.");
}

void loop() {
  int currentState = digitalRead(SENSOR_PIN);
  unsigned long now = millis();

  if (currentState != lastState || now - lastReportTime >= 1000) {
    lastState = currentState;
    lastReportTime = now;

    Serial.print("Sensor state: ");
    Serial.print(currentState);
    Serial.println(currentState == HIGH ? " (HIGH)" : " (LOW)");
  }

  delay(10);
}
