package io.hyperhealth.connect.controlplane.secret;

import java.util.Objects;

/** Portable metadata projection that deliberately requires rebinding in the target environment. */
public record SecretReferenceExport(
        String secretReferenceId,
        SecretProvider provider,
        SecretPurpose purpose,
        SecretReferenceState state,
        long rowVersion,
        boolean requiresRebinding) {

    public SecretReferenceExport {
        Objects.requireNonNull(secretReferenceId, "secretReferenceId");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(state, "state");
        SecretReferenceId parsedId = SecretReferenceId.parse(secretReferenceId);
        if (!parsedId.externalForm().equals(secretReferenceId)) {
            throw new IllegalArgumentException("Secret reference export identifier must be canonical");
        }
        if (rowVersion < 0) {
            throw new IllegalArgumentException("Secret reference export rowVersion must be non-negative");
        }
        if (!requiresRebinding) {
            throw new IllegalArgumentException("A secret reference export must require target rebinding");
        }
    }

    static SecretReferenceExport from(SecretReference reference) {
        Objects.requireNonNull(reference, "reference");
        return new SecretReferenceExport(
                reference.id().externalForm(),
                reference.provider(),
                reference.purpose(),
                reference.state(),
                reference.rowVersion(),
                true);
    }
}
