package io.hyperhealth.connect.controlplane.inventory;

/** Uniform absence result used for missing and non-visible inventory resources. */
public final class InventoryResourceNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InventoryResourceNotFoundException() {
        super("Inventory resource not found");
    }
}
