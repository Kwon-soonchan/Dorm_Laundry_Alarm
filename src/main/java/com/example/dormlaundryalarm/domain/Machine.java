// src/main/java/com/example/dormlaundryalarm/domain/Machine.java
package com.example.dormlaundryalarm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
public class Machine {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String macAddress; // NodeMCU 기기 식별용

    private String machineType; // "WASHER" or "DRYER"

    private String status; // "AVAILABLE", "RUNNING", "FINISHED"

    public Machine(String macAddress, String machineType, String status) {
        this.macAddress = macAddress;
        this.machineType = machineType;
        this.status = status;
    }

    // 비즈니스 로직: 상태 변경 메서드
    public void updateStatus(String newStatus) {
        this.status = newStatus;
    }
}