// src/main/java/com/example/dormlaundryalarm/repository/MachineRepository.java
package com.example.dormlaundryalarm.repository;

import com.example.dormlaundryalarm.domain.Machine;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MachineRepository extends JpaRepository<Machine, Long> {
}