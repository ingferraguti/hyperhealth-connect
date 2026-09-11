package io.hyperhealth.connect.controlplane.inventory;

import java.util.Objects;

import io.hyperhealth.connect.controlplane.inventory.InventoryId.EndpointId;
import io.hyperhealth.connect.controlplane.inventory.InventoryPath.EndpointPath;

/** Protocol endpoint owned by an application. Address and credentials are separate concerns. */
public record Endpoint(EndpointId id, Application parent) implements ScopedInventoryResource {
    public Endpoint {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(parent, "parent");
    }

    public static Endpoint createUnder(Application parent) {
        return new Endpoint(EndpointId.newId(), parent);
    }

    @Override
    public EndpointPath path() {
        return new EndpointPath(parent.path(), id);
    }
}
