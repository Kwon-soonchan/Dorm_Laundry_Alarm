package com.example.dormlaundryalarm.service;

import java.time.Duration;

public record DeliveryResult(Outcome outcome, String code, Duration retryAfter, String messageId) {
    public enum Outcome { SENT, RETRY, FAILED, UNCERTAIN }
    public static DeliveryResult sent(String id) { return new DeliveryResult(Outcome.SENT, null, Duration.ZERO, id); }
    public static DeliveryResult retry(String code, Duration wait) { return new DeliveryResult(Outcome.RETRY, code, wait, null); }
    public static DeliveryResult failed(String code) { return new DeliveryResult(Outcome.FAILED, code, Duration.ZERO, null); }
    public static DeliveryResult uncertain(String code) { return new DeliveryResult(Outcome.UNCERTAIN, code, Duration.ZERO, null); }
}
