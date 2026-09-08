package io.hyperhealth.connect.sdk;

import java.util.Objects;
import java.util.Set;

public record ConnectorDescriptor(String id, String version, String sdkRange, Set<String> capabilities) {
    public ConnectorDescriptor {
        id = Objects.requireNonNull(id, "id").strip();
        version = Objects.requireNonNull(version, "version").strip();
        sdkRange = Objects.requireNonNull(sdkRange, "sdkRange").strip();
        capabilities = Set.copyOf(capabilities);
        if (id.isEmpty() || version.isEmpty() || sdkRange.isEmpty()) {
            throw new IllegalArgumentException("Connector identity values must not be blank");
        }
    }
}

