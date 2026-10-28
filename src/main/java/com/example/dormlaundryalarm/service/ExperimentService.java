package com.example.dormlaundryalarm.service;

import com.example.dormlaundryalarm.domain.Experiment;
import com.example.dormlaundryalarm.domain.ExperimentEvent;
import com.example.dormlaundryalarm.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ExperimentService {
    private final ExperimentRepository experiments;
    private final ExperimentEventRepository events;
    private final PlatformTransactionManager transactionManager;

    public synchronized Experiment start(String title, Long machineId) {
        return new TransactionTemplate(transactionManager).execute(status -> {
        if (machineId == null || machineId < 1 || title == null || title.isBlank() || title.strip().length() > 80)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "기기 번호와 1~80자의 제목이 필요합니다.");
        if (experiments.existsByMachineIdAndEndedAtIsNull(machineId))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "진행 중인 실험을 먼저 종료하세요.");
        Experiment experiment = experiments.save(new Experiment(title.strip(), machineId));
        events.save(new ExperimentEvent(experiment.getId(), ExperimentEvent.Kind.START, ""));
        return experiment;
        });
    }

    @Transactional(readOnly = true)
    public Experiment get(Long id) {
        return experiments.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "실험을 찾을 수 없습니다."));
    }

    @Transactional
    public ExperimentEvent mark(Long id, String kind, String note) {
        Experiment experiment = get(id);
        if (experiment.getEndedAt() != null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "종료된 실험에는 기록할 수 없습니다.");
        ExperimentEvent.Kind parsed;
        try { parsed = ExperimentEvent.Kind.valueOf(kind == null ? "" : kind); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 기록 종류입니다."); }
        if (parsed == ExperimentEvent.Kind.START || note != null && note.length() > 300)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "메모는 300자 이내로 입력하세요.");
        return events.save(new ExperimentEvent(id, parsed, note == null ? "" : note.strip()));
    }

    @Transactional
    public Experiment finish(Long id) {
        Experiment experiment = get(id);
        if (experiment.getEndedAt() == null) experiment.finish();
        return experiment;
    }
}
