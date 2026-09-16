package io.hyperhealth.connect.controlplane.inventory;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

/** Digested idempotency metadata; raw keys and raw subjects never reach persistence. */
public record EndpointIdempotency(
        byte[] subjectDigest,
        byte[] keyDigest,
        byte[] requestDigest,
        Instant expiresAt) {

    private static final int SHA_256_BYTES = 32;

    public EndpointIdempotency {
        subjectDigest = copyDigest(subjectDigest, "subjectDigest");
        keyDigest = copyDigest(keyDigest, "keyDigest");
        requestDigest = copyDigest(requestDigest, "requestDigest");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    @Override
    public byte[] subjectDigest() {
        return Arrays.copyOf(subjectDigest, subjectDigest.length);
    }

    @Override
    public byte[] keyDigest() {
        return Arrays.copyOf(keyDigest, keyDigest.length);
    }

    @Override
    public byte[] requestDigest() {
        return Arrays.copyOf(requestDigest, requestDigest.length);
    }

    private static byte[] copyDigest(byte[] value, String name) {
        Objects.requireNonNull(value, name);
        if (value.length != SHA_256_BYTES) {
            throw new IllegalArgumentException(name + " must be a SHA-256 digest");
        }
        return Arrays.copyOf(value, value.length);
    }
}
