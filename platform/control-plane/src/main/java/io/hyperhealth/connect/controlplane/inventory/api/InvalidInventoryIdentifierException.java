package io.hyperhealth.connect.controlplane.inventory.api;

/** Safe API error for a malformed typed inventory identifier. */
public final class InvalidInventoryIdentifierException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InvalidInventoryIdentifierException(Throwable cause) {
        super("Inventory identifier is invalid", cause);
    }
}
