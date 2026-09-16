package io.hyperhealth.connect.controlplane.inventory.api;

import java.io.Serial;

/** Raised when a governed mutation omits its required If-Match precondition. */
public final class MissingInventoryPreconditionException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public MissingInventoryPreconditionException() {
        super("If-Match is required");
    }
}
