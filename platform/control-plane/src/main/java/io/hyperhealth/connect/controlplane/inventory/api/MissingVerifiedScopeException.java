package io.hyperhealth.connect.controlplane.inventory.api;

/** Raised before controller invocation when no trusted scope was attached to the request. */
public final class MissingVerifiedScopeException extends SecurityException {
    private static final long serialVersionUID = 1L;

    public MissingVerifiedScopeException() {
        super("A verified inventory scope is required");
    }
}
