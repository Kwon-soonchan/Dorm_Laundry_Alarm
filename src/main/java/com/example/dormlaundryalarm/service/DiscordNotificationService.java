// src/main/java/com/example/dormlaundryalarm/service/DiscordNotificationService.java
package com.example.dormlaundryalarm.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class DiscordNotificationService implements NotificationService {

    // 🌟 캡스톤 프로젝트처럼 yml 파일에서 URL을 동적으로 읽어옵니다!
    @Value("${discord.webhook.url}")
    private String webhookUrl;

    private final RestClient restClient;

    public DiscordNotificationService() {
        this.restClient = RestClient.create();
    }

    @Override
    public void sendLaundryCompleteNotification(Long machineId) {
        String messageContent = "{\"content\": \"🧺 알림: " + machineId + "번 세탁기의 작동이 완료되었습니다! 빨래를 수거해 주세요.\"}";

        try {
            restClient.post()
                    .uri(webhookUrl) // 하드코딩된 변수 대신 yml에서 가져온 변수 사용
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "DormLaundryAlarm/1.0")
                    .body(messageContent)
                    .retrieve()
                    .toBodilessEntity();

            System.out.println("디스코드 알림 전송 완료! (Machine ID: " + machineId + ")");
        } catch (Exception e) {
            System.err.println("❌ [디스코드 전송 실패] " + e.getMessage());
        }
    }
}