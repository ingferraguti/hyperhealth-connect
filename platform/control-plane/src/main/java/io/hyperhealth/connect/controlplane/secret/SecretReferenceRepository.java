package io.hyperhealth.connect.controlplane.secret;

import java.util.List;
import java.util.Optional;

import io.hyperhealth.connect.controlplane.inventory.InventoryId.EndpointId;
import io.hyperhealth.connect.controlplane.inventory.VerifiedFacilityScope;

/** Scope-first persistence boundary for secret references and endpoint bindings. */
public interface SecretReferenceRepository {

    void register(VerifiedFacilityScope scope, SecretReference reference);

    Optional<SecretReference> findUsable(VerifiedFacilityScope scope, SecretReferenceId referenceId);

    void bindToEndpoint(VerifiedFacilityScope scope, EndpointId endpointId, SecretReferenceId referenceId);

    List<SecretReferenceExport> exportEndpointReferences(VerifiedFacilityScope scope, EndpointId endpointId);
}
