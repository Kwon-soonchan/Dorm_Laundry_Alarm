package com.example.dormlaundryalarm.controller;

import com.example.dormlaundryalarm.domain.*;
import com.example.dormlaundryalarm.repository.*;
import com.example.dormlaundryalarm.service.ExperimentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.List;

@RestController
@RequestMapping("/api/experiments")
@RequiredArgsConstructor
public class ExperimentController {
    private final ExperimentService service;
    private final ExperimentRepository experiments;
    private final ExperimentEventRepository events;
    private final VibrationTelemetryRepository telemetry;
    public record StartRequest(String title, Long machineId) {}
    public record EventRequest(String kind, String note) {}

    @GetMapping
    public List<Experiment> list(@RequestParam(defaultValue = "1") Long machineId) {
        if (machineId < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        return experiments.findTop50ByMachineIdOrderByStartedAtDesc(machineId);
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Experiment start(@RequestBody StartRequest request) {
        return service.start(request.title(), request.machineId());
    }
    @GetMapping("/{id}/events")
    public List<ExperimentEvent> events(@PathVariable Long id) {
        service.get(id);
        return events.findByExperimentIdOrderByRecordedAtAsc(id);
    }
    @PostMapping("/{id}/events") @ResponseStatus(HttpStatus.CREATED)
    public ExperimentEvent mark(@PathVariable Long id, @RequestBody EventRequest request) {
        return service.mark(id, request.kind(), request.note());
    }
    @PostMapping("/{id}/finish")
    public Experiment finish(@PathVariable Long id) { return service.finish(id); }

    @GetMapping(value = "/{id}/events.csv", produces = "text/csv")
    public ResponseEntity<byte[]> eventCsv(@PathVariable Long id) {
        service.get(id);
        StringBuilder csv = new StringBuilder("id,experiment_id,recorded_at,kind,note\n");
        for (ExperimentEvent e : events.findByExperimentIdOrderByRecordedAtAsc(id))
            csv.append(e.getId()).append(',').append(id).append(',').append(e.getRecordedAt())
                    .append(',').append(e.getKind()).append(',').append(csvText(e.getNote())).append('\n');
        return csv(csv.toString(), "experiment-" + id + "-events.csv");
    }

    @GetMapping(value = "/{id}/telemetry.csv", produces = "text/csv")
    public ResponseEntity<byte[]> telemetryCsv(@PathVariable Long id) {
        Experiment e = service.get(id);
        ZoneId zone = ZoneId.systemDefault();
        List<VibrationTelemetry> rows = telemetry.findByMachineIdAndRecordedAtBetweenOrderByRecordedAtAsc(
                e.getMachineId(), LocalDateTime.ofInstant(e.getStartedAt(), zone),
                LocalDateTime.ofInstant(e.getEndedAt() == null ? Instant.now() : e.getEndedAt(), zone));
        StringBuilder csv = new StringBuilder("id,machine_id,recorded_at,window_ms,sample_count,vibration_count,vibration_ratio\n");
        for (VibrationTelemetry row : rows)
            csv.append(row.getId()).append(',').append(row.getMachineId()).append(',')
                    .append(row.getRecordedAt().atZone(zone).toInstant()).append(',').append(row.getWindowMs())
                    .append(',').append(row.getSampleCount()).append(',').append(row.getVibrationCount())
                    .append(',').append(row.getVibrationRatio()).append('\n');
        return csv(csv.toString(), "experiment-" + id + "-telemetry.csv");
    }

    // Prevent spreadsheet formula execution when user notes are opened in Excel.
    static String csvText(String text) {
        String value = text == null ? "" : text;
        if (value.stripLeading().matches("(?s)^[=+@-].*")) value = "'" + value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    private ResponseEntity<byte[]> csv(String content, String filename) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(("\uFEFF" + content).getBytes(StandardCharsets.UTF_8));
    }
}
