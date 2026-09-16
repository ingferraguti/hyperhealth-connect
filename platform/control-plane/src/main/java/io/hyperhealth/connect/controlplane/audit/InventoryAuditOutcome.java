package io.hyperhealth.connect.controlplane.audit;

/** Explicit audit outcome; it is never inferred from log severity. */
public enum InventoryAuditOutcome {
    SUCCESS,
    FAILURE,
    DENIED
}
