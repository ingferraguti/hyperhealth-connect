package io.hyperhealth.connect.controlplane.inventory;

/** A resource that occupies exactly one node in the normative inventory hierarchy. */
public sealed interface ScopedInventoryResource
        permits Tenant, Organization, Facility, Application, Endpoint {

    InventoryId id();

    InventoryPath path();
}
