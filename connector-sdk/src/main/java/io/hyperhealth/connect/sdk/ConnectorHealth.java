package io.hyperhealth.connect.sdk;

import java.time.Instant;
import java.util.Objects;

public record ConnectorHealth(Status status, Instant observedAt, String reasonCode) {
    public ConnectorHealth {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(observedAt, "observedAt");
        reasonCode = Objects.requireNonNull(reasonCode, "reasonCode").strip();
    }

    public enum Status { UP, DEGRADED, DOWN }
}

