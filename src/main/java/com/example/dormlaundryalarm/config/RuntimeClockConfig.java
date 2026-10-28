package com.example.dormlaundryalarm.config;

import org.springframework.context.annotation.*;
import java.time.Clock;

@Configuration
public class RuntimeClockConfig {
    @Bean public Clock runtimeClock() { return Clock.systemUTC(); }
}
