package io.hyperhealth.connect.security;

/** Raised before a resource is read when the caller and resource scopes differ. */
public final class CrossTenantAccessException extends SecurityException {
    private static final long serialVersionUID = 1L;

    public CrossTenantAccessException(String message) {
        super(message);
    }
}

