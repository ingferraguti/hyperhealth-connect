package io.hyperhealth.connect.controlplane.inventory;

import java.util.Objects;
import java.util.Optional;

/** Explicit, allowlisted partial update. */
public record EndpointPatch(
        Optional<String> displayName,
        Optional<InventoryLifecycleState> lifecycleState) {

    public EndpointPatch {
        displayName = Objects.requireNonNull(displayName, "displayName");
        lifecycleState = Objects.requireNonNull(lifecycleState, "lifecycleState");
        if (displayName.isEmpty() && lifecycleState.isEmpty()) {
            throw new IllegalArgumentException("at least one endpoint property must be supplied");
        }
    }
}
