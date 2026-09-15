package io.hyperhealth.connect.controlplane.secret;

import java.util.Objects;
import java.util.UUID;

/** Immutable, opaque identifier for a reference to externally managed secret material. */
public record SecretReferenceId(UUID value) {

    private static final String PREFIX = "sref-";

    public SecretReferenceId {
        Objects.requireNonNull(value, "value");
        if (value.getMostSignificantBits() == 0L && value.getLeastSignificantBits() == 0L) {
            throw new IllegalArgumentException("The nil UUID cannot identify a secret reference");
        }
    }

    public static SecretReferenceId newId() {
        return new SecretReferenceId(UUID.randomUUID());
    }

    public static SecretReferenceId parse(String externalForm) {
        Objects.requireNonNull(externalForm, "externalForm");
        if (!externalForm.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Secret reference identifier must start with " + PREFIX);
        }
        String rawValue = externalForm.substring(PREFIX.length());
        try {
            UUID parsed = UUID.fromString(rawValue);
            if (!parsed.toString().equals(rawValue)) {
                throw new IllegalArgumentException("Secret reference identifier is not canonical");
            }
            return new SecretReferenceId(parsed);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Secret reference identifier is not canonical", exception);
        }
    }

    public String externalForm() {
        return PREFIX + value;
    }

    @Override
    public String toString() {
        return externalForm();
    }
}
