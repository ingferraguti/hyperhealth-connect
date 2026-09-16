package io.hyperhealth.connect.controlplane.inventory;

import java.util.Objects;

import io.hyperhealth.connect.controlplane.audit.InventoryActorType;

/** Minimal authenticated actor context required by idempotency and audit boundaries. */
public record InventoryActor(String subject, String authorizedParty, InventoryActorType actorType) {
    public InventoryActor {
        subject = requireBounded(subject, "subject", 512);
        authorizedParty = requireBounded(authorizedParty, "authorizedParty", 256);
        Objects.requireNonNull(actorType, "actorType");
    }

    private static String requireBounded(String value, String name, int maximumLength) {
        Objects.requireNonNull(value, name);
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.length() > maximumLength) {
            throw new IllegalArgumentException(name + " is outside the allowed length");
        }
        return normalized;
    }
}
