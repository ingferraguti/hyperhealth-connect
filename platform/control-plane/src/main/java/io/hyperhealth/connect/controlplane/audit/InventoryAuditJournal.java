package io.hyperhealth.connect.controlplane.audit;

import io.hyperhealth.connect.controlplane.inventory.VerifiedFacilityScope;

/** Transaction-participating append-only audit port. */
public interface InventoryAuditJournal {
    void append(InventoryAuditIntent intent);

    AuditChainVerification verify(VerifiedFacilityScope scope);
}
