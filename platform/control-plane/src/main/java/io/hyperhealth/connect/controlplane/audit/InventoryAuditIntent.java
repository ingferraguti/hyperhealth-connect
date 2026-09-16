package io.hyperhealth.connect.controlplane.audit;

import java.util.Objects;

import io.hyperhealth.connect.controlplane.inventory.InventoryActor;
import io.hyperhealth.connect.controlplane.inventory.VerifiedFacilityScope;

/** Safe in-memory input; raw actor/resource identifiers are HMACed before persistence. */
public record InventoryAuditIntent(
        VerifiedFacilityScope scope,
        InventoryAuditContext context,
        InventoryActor actor,
        InventoryAuditAction action,
        InventoryAuditOutcome outcome,
        String reasonCode,
        InventoryAuditResourceType resourceType,
        String resourceKey,
        String detailFingerprint) {

    public InventoryAuditIntent {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(resourceType, "resourceType");
        resourceKey = bounded(resourceKey, "resourceKey", 1024);
        detailFingerprint = bounded(detailFingerprint, "detailFingerprint", 1024);
        if (reasonCode != null) {
            reasonCode = bounded(reasonCode, "reasonCode", 128);
        }
        if (outcome != InventoryAuditOutcome.SUCCESS && reasonCode == null) {
            throw new IllegalArgumentException("reasonCode is required for non-success outcomes");
        }
    }

    private static String bounded(String value, String name, int maximumLength) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(name + " is outside the allowed length");
        }
        return value;
    }
}
