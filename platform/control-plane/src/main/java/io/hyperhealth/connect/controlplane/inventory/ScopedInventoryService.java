package io.hyperhealth.connect.controlplane.inventory;

import java.util.Objects;

import io.hyperhealth.connect.controlplane.inventory.InventoryId.EndpointId;

/** Application service that keeps verified scope mandatory at its public boundary. */
public final class ScopedInventoryService {

    private final ScopedEndpointRepository endpointRepository;

    public ScopedInventoryService(ScopedEndpointRepository endpointRepository) {
        this.endpointRepository = Objects.requireNonNull(endpointRepository, "endpointRepository");
    }

    public ScopedEndpoint getEndpoint(VerifiedFacilityScope scope, EndpointId endpointId) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(endpointId, "endpointId");
        return endpointRepository
                .findEndpoint(scope, endpointId)
                .orElseThrow(InventoryResourceNotFoundException::new);
    }
}
