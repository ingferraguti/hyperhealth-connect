package io.hyperhealth.connect.controlplane.inventory;

import java.util.Objects;

import io.hyperhealth.connect.controlplane.inventory.InventoryId.ApplicationId;
import io.hyperhealth.connect.controlplane.inventory.InventoryPath.ApplicationPath;

/** Source or destination application located within a facility. */
public record Application(ApplicationId id, Facility parent) implements ScopedInventoryResource {
    public Application {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(parent, "parent");
    }

    public static Application createUnder(Facility parent) {
        return new Application(ApplicationId.newId(), parent);
    }

    @Override
    public ApplicationPath path() {
        return new ApplicationPath(parent.path(), id);
    }
}
