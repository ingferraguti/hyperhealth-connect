package io.hyperhealth.connect.controlplane.inventory;

import java.util.Objects;

/** Creation result retaining whether the transaction was replayed from its idempotency record. */
public record EndpointCreationResult(ScopedEndpoint endpoint, boolean replayed) {
    public EndpointCreationResult {
        Objects.requireNonNull(endpoint, "endpoint");
    }
}
