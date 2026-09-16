package io.hyperhealth.connect.controlplane.inventory;

import java.util.Optional;

import io.hyperhealth.connect.controlplane.inventory.InventoryId.ApplicationId;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.EndpointId;

/** Persistence port whose operations cannot be invoked without an explicit scope. */
public interface ScopedEndpointRepository {
    Optional<ScopedEndpoint> findEndpoint(VerifiedFacilityScope scope, EndpointId endpointId);

    EndpointPageSlice listEndpoints(VerifiedFacilityScope scope, EndpointQuery query);

    EndpointCreationResult createEndpoint(
            VerifiedFacilityScope scope,
            InventoryActor actor,
            EndpointIdempotency idempotency,
            ApplicationId applicationId,
            String displayName);

    ScopedEndpoint updateEndpoint(
            VerifiedFacilityScope scope,
            EndpointId endpointId,
            long expectedRowVersion,
            EndpointPatch patch);
}
