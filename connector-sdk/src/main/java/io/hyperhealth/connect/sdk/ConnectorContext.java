package io.hyperhealth.connect.sdk;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;

public record ConnectorContext(String instanceId, Clock clock, Map<String, String> configuration) {
    public ConnectorContext {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(clock, "clock");
        configuration = Map.copyOf(configuration);
    }
}

