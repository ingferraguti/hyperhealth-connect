package io.hyperhealth.connect.controlplane.inventory;

import java.util.Objects;
import java.util.Optional;

import io.hyperhealth.connect.controlplane.inventory.InventoryId.ApplicationId;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.EndpointId;

/** Allowlisted, keyset-paginated endpoint query. */
public record EndpointQuery(
        Optional<ApplicationId> applicationId,
        Optional<InventoryLifecycleState> lifecycleState,
        Optional<EndpointId> afterEndpointId,
        int limit) {

    public static final int MAXIMUM_LIMIT = 100;

    public EndpointQuery {
        applicationId = Objects.requireNonNull(applicationId, "applicationId");
        lifecycleState = Objects.requireNonNull(lifecycleState, "lifecycleState");
        afterEndpointId = Objects.requireNonNull(afterEndpointId, "afterEndpointId");
        if (lifecycleState.orElse(null) == InventoryLifecycleState.DECOMMISSIONED) {
            throw new InvalidInventoryRequestException(
                    "decommissioned endpoints are not part of the current inventory view");
        }
        if (limit < 1 || limit > MAXIMUM_LIMIT) {
            throw new InvalidInventoryRequestException(
                    "limit must be between 1 and " + MAXIMUM_LIMIT);
        }
    }
}
