package io.hyperhealth.connect.controlplane.inventory;

import java.util.Objects;

import io.hyperhealth.connect.controlplane.inventory.InventoryId.FacilityId;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.TenantId;

/**
 * Immutable tenant and facility scope established by a trusted server-side boundary.
 *
 * <p>The HTTP layer never constructs this value from tenant or facility headers. P1-0104 will
 * derive it from authenticated grants and server-side inventory assignments.</p>
 */
public record VerifiedFacilityScope(TenantId tenantId, FacilityId facilityId) {
    public VerifiedFacilityScope {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(facilityId, "facilityId");
    }
}
