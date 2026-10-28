package com.example.dormlaundryalarm.service;

public interface NotificationService {
    boolean isConfigured();
    DeliveryResult sendLaundryCompleteNotification(Long machineId, String cycleId);
}
