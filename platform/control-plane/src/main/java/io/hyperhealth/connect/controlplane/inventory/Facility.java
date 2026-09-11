package io.hyperhealth.connect.controlplane.inventory;

import java.util.Objects;

import io.hyperhealth.connect.controlplane.inventory.InventoryId.FacilityId;
import io.hyperhealth.connect.controlplane.inventory.InventoryPath.FacilityPath;

/** Hospital, laboratory, territorial site, or other facility under an organization. */
public record Facility(FacilityId id, Organization parent) implements ScopedInventoryResource {
    public Facility {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(parent, "parent");
    }

    public static Facility createUnder(Organization parent) {
        return new Facility(FacilityId.newId(), parent);
    }

    @Override
    public FacilityPath path() {
        return new FacilityPath(parent.path(), id);
    }
}
