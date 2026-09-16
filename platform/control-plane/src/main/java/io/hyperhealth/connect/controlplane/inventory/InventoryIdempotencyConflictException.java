package io.hyperhealth.connect.controlplane.inventory;

import java.io.Serial;

/** Raised when the same idempotency key is reused for a different canonical request. */
public final class InventoryIdempotencyConflictException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public InventoryIdempotencyConflictException() {
        super("The idempotency key was already used for a different request");
    }
}
