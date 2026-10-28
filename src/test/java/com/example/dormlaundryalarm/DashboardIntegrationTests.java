package com.example.dormlaundryalarm;

import com.example.dormlaundryalarm.domain.VibrationTelemetry;
import com.example.dormlaundryalarm.repository.VibrationTelemetryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import java.net.URI;
import java.net.http.*;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DashboardIntegrationTests {
    @LocalServerPort int port;
    @Autowired VibrationTelemetryRepository telemetry;
    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    private HttpResponse<String> request(String method, String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(java.time.Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() :
                        HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void dashboardServesPageAndDistinguishesMissingStaleAndLiveData() throws Exception {
        assertEquals(200, request("GET", "/", null).statusCode());
        assertTrue(request("GET", "/", null).body().contains("Laundry Lab"));
        assertTrue(request("GET", "/api/dashboard?machineId=991", null).body().contains("\"WAITING\""));
        VibrationTelemetry old = new VibrationTelemetry(992L, 1000, 100, 25);
        var field = VibrationTelemetry.class.getDeclaredField("recordedAt");
        field.setAccessible(true);
        field.set(old, LocalDateTime.now().minusSeconds(30));
        telemetry.save(old);
        assertTrue(request("GET", "/api/dashboard?machineId=992", null).body().contains("\"STALE\""));
        telemetry.save(new VibrationTelemetry(993L, 1000, 100, 75));
        String snapshot = request("GET", "/api/dashboard?machineId=993", null).body();
        assertTrue(snapshot.contains("\"LIVE\""));
        assertTrue(snapshot.contains("0.75"));
        assertTrue(snapshot.contains("\"totalSamples\":1"));
        assertEquals(400, request("GET", "/api/dashboard?minutes=9999", null).statusCode());
        assertEquals(400, request("GET", "/api/dashboard?machineId=0", null).statusCode());
    }

    @Test
    void experimentsPersistMarkersBoundExportsAndRejectInvalidTransitions() throws Exception {
        String payload = "{\"title\":\"Integration cycle\",\"machineId\":994}";
        var created = request("POST", "/api/experiments", payload);
        assertEquals(201, created.statusCode());
        // The serialized experiment contains one numeric ID and no nested entities.
        var matcher = java.util.regex.Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(created.body());
        assertTrue(matcher.find());
        String id = matcher.group(1), base = "/api/experiments/" + id;
        assertEquals(409, request("POST", "/api/experiments", payload).statusCode());
        assertEquals(400, request("POST", base + "/events", "{\"kind\":\"BOGUS\"}").statusCode());
        assertEquals(400, request("POST", base + "/events", "{\"kind\":\"START\"}").statusCode());
        assertEquals(201, request("POST", base + "/events", "{\"kind\":\"SPIN\",\"note\":\"=1+1\"}").statusCode());
        assertTrue(request("GET", base + "/events", null).body().contains("\"SPIN\""));
        telemetry.save(new VibrationTelemetry(994L, 1000, 100, 61));
        telemetry.save(new VibrationTelemetry(995L, 1000, 100, 99));
        assertTrue(request("GET", base + "/telemetry.csv", null).body().contains("0.61"));
        assertFalse(request("GET", base + "/telemetry.csv", null).body().contains("0.99"));
        assertTrue(request("GET", base + "/events.csv", null).body().contains("'=1+1"));
        assertEquals(200, request("POST", base + "/finish", "{}").statusCode());
        assertEquals(409, request("POST", base + "/events", "{\"kind\":\"FINISH\"}").statusCode());
        assertTrue(request("GET", "/api/experiments?machineId=994", null).body().contains("Integration cycle"));
        assertEquals(400, request("POST", "/api/experiments", "{\"title\":\" \",\"machineId\":1}").statusCode());
        assertEquals(404, request("GET", "/api/experiments/999999/events", null).statusCode());
    }
}
