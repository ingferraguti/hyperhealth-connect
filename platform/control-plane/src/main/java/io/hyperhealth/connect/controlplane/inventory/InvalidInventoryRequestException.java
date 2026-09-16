package io.hyperhealth.connect.controlplane.inventory;

import java.io.Serial;

/** Stable domain signal for malformed or unsupported inventory input. */
public final class InvalidInventoryRequestException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidInventoryRequestException(String message) {
        super(message);
    }

    public InvalidInventoryRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
