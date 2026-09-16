package io.hyperhealth.connect.controlplane.audit;

/** Stable action vocabulary for the P1-0107 inventory vertical slice. */
public enum InventoryAuditAction {
    ENDPOINT_READ,
    ENDPOINT_LIST,
    ENDPOINT_CREATE,
    ENDPOINT_UPDATE,
    ENDPOINT_DECOMMISSION
}
