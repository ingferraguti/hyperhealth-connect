package io.hyperhealth.connect.controlplane.inventory;

import java.util.Objects;

import io.hyperhealth.connect.controlplane.inventory.InventoryId.OrganizationId;
import io.hyperhealth.connect.controlplane.inventory.InventoryPath.OrganizationPath;

/** Legal or administrative organization belonging to one tenant. */
public record Organization(OrganizationId id, Tenant parent) implements ScopedInventoryResource {
    public Organization {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(parent, "parent");
    }

    public static Organization createUnder(Tenant parent) {
        return new Organization(OrganizationId.newId(), parent);
    }

    @Override
    public OrganizationPath path() {
        return new OrganizationPath(parent.path(), id);
    }
}
