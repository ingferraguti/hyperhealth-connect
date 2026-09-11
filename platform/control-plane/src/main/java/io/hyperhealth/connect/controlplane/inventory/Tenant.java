package io.hyperhealth.connect.controlplane.inventory;

import java.util.Objects;

import io.hyperhealth.connect.controlplane.inventory.InventoryId.TenantId;
import io.hyperhealth.connect.controlplane.inventory.InventoryPath.TenantPath;

/** Root contractual and policy boundary. */
public record Tenant(TenantId id) implements ScopedInventoryResource {
    public Tenant {
        Objects.requireNonNull(id, "id");
    }

    public static Tenant create() {
        return new Tenant(TenantId.newId());
    }

    @Override
    public TenantPath path() {
        return new TenantPath(id);
    }
}
