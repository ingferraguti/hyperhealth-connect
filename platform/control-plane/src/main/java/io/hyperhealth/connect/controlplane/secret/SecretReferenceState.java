package io.hyperhealth.connect.controlplane.secret;

/** Lifecycle metadata only; secret material and provider versions remain outside the Platform DB. */
public enum SecretReferenceState {
    ACTIVE,
    ROTATING,
    REVOKED
}
