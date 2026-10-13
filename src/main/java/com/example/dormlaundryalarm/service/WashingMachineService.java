// src/main/java/com/example/dormlaundryalarm/service/WashingMachineService.java
package com.example.dormlaundryalarm.service;

import com.example.dormlaundryalarm.domain.Machine;
import com.example.dormlaundryalarm.repository.MachineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class WashingMachineService {

    private final MachineRepository machineRepository;
    private final NotificationService notificationService;

    // 기기별 '마지막 진동 감지 시간'을 메모리에 기록하는 장부
    private final Map<Long, LocalDateTime> lastVibrationMap = new ConcurrentHashMap<>();

    @Value("${laundry.completion-threshold-seconds:180}")
    private long completionThresholdSeconds;

    @Transactional
    public void processVibration(String topic, String payload) {
        String[] parts = topic.split("/");
        Long machineId = Long.parseLong(parts[parts.length - 1]);

        Machine machine = machineRepository.findById(machineId)
                .orElseGet(() -> machineRepository.save(new Machine("MAC-001", "WASHER", "AVAILABLE")));

        LocalDateTime now = LocalDateTime.now();

        if ("1".equals(payload)) {
            // 🎯 진동이 감지되면 마지막 진동 시간을 계속 '현재'로 갱신
            lastVibrationMap.put(machineId, now);

            if (!"RUNNING".equals(machine.getStatus())) {
                machine.updateStatus("RUNNING");
                System.out.println("🔄 " + machineId + "번 세탁기 작동 시작 감지!");
            }
        }
        else if ("0".equals(payload) && "RUNNING".equals(machine.getStatus())) {
            // 진동이 멈춘 상태이고, DB상 세탁기가 돌아가는 중일 때

            // 장부에서 마지막 진동 시간을 가져옴 (없으면 현재 시간)
            LocalDateTime lastVibration = lastVibrationMap.getOrDefault(machineId, now);

            // 마지막 진동으로부터 몇 초나 지났는지 계산
            long secondsSinceLastVibration = ChronoUnit.SECONDS.between(lastVibration, now);

            // 무진동 상태가 설정한 임계값을 넘으면 완료 후보로 판정한다.
            if (secondsSinceLastVibration >= completionThresholdSeconds) {
                machine.updateStatus("AVAILABLE");
                System.out.println("✅ " + machineId + "번 세탁기 작동 완료 확정! (무진동 " + completionThresholdSeconds + "초 유지)");

                notificationService.sendLaundryCompleteNotification(machineId);

                // 알림을 보냈으니 장부에서 기록 삭제
                lastVibrationMap.remove(machineId);
            }
            // 임계값 이내의 무진동은 헹굼/탈수 대기 중인 일시 정지 구간으로 간주한다.
        }
    }
}
