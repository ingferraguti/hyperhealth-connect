package io.hyperhealth.connect.controlplane.inventory;

import java.util.Optional;

import io.hyperhealth.connect.controlplane.inventory.InventoryId.EndpointId;

/** Persistence port whose read operations cannot be invoked without an explicit scope. */
public interface ScopedEndpointRepository {
    Optional<ScopedEndpoint> findEndpoint(VerifiedFacilityScope scope, EndpointId endpointId);
}
