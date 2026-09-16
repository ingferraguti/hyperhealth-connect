package io.hyperhealth.connect.controlplane.inventory;

import java.io.Serial;

/** Raised when a requested lifecycle transition is not allowed. */
public final class InventoryLifecycleConflictException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public InventoryLifecycleConflictException() {
        super("The requested lifecycle transition is not allowed");
    }
}
