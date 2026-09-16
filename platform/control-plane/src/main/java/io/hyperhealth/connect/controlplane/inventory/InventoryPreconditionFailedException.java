package io.hyperhealth.connect.controlplane.inventory;

import java.io.Serial;

/** Raised when an optimistic row version no longer matches. */
public final class InventoryPreconditionFailedException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public InventoryPreconditionFailedException() {
        super("The inventory resource changed after it was read");
    }
}
