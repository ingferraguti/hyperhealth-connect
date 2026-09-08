package io.hyperhealth.connect.sdk;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DeliveryReceipt(UUID attemptId, Outcome outcome, Instant completedAt, String code) {
    public DeliveryReceipt {
        Objects.requireNonNull(attemptId, "attemptId");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(completedAt, "completedAt");
        code = Objects.requireNonNull(code, "code").strip();
    }

    public enum Outcome { DELIVERED, REJECTED, UNKNOWN }
}

