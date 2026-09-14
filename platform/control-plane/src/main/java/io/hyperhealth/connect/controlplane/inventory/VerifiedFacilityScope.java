package io.hyperhealth.connect.controlplane.inventory;

import java.util.Objects;

import io.hyperhealth.connect.controlplane.inventory.InventoryId.FacilityId;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.TenantId;

/**
 * Immutable tenant and facility scope established by a trusted server-side boundary.
 *
 * <p>The HTTP layer never constructs this value from tenant or facility headers. The OIDC security
 * boundary derives it only from cryptographically verified, audience-bound claims.</p>
 */
public record VerifiedFacilityScope(TenantId tenantId, FacilityId facilityId) {
    public VerifiedFacilityScope {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(facilityId, "facilityId");
    }
}
