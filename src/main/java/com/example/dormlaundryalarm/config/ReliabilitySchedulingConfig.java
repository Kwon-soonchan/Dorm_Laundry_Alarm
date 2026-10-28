package com.example.dormlaundryalarm.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "reliability.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class ReliabilitySchedulingConfig {}
