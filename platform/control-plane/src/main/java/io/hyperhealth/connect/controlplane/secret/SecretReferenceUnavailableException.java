package io.hyperhealth.connect.controlplane.secret;

/** Uniform failure for absent, revoked or cross-scope secret references and endpoint bindings. */
public final class SecretReferenceUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SecretReferenceUnavailableException() {
        super("The secret reference does not exist or is not usable in the current scope");
    }
}
