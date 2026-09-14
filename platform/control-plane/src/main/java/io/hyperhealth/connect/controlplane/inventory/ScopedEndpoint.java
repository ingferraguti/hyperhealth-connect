package io.hyperhealth.connect.controlplane.inventory;

import java.util.Objects;

import io.hyperhealth.connect.controlplane.inventory.InventoryId.ApplicationId;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.EndpointId;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.FacilityId;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.OrganizationId;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.TenantId;

/** Safe read model returned only after tenant and facility predicates have matched. */
public record ScopedEndpoint(
        EndpointId endpointId,
        TenantId tenantId,
        OrganizationId organizationId,
        FacilityId facilityId,
        ApplicationId applicationId,
        String displayName,
        InventoryLifecycleState lifecycleState,
        long rowVersion) {

    public ScopedEndpoint {
        Objects.requireNonNull(endpointId, "endpointId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(facilityId, "facilityId");
        Objects.requireNonNull(applicationId, "applicationId");
        displayName = requireText(displayName, "displayName");
        Objects.requireNonNull(lifecycleState, "lifecycleState");
        if (rowVersion < 0) {
            throw new IllegalArgumentException("rowVersion must not be negative");
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
