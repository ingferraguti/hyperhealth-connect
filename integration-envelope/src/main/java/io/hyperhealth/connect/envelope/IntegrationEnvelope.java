package io.hyperhealth.connect.envelope;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Minimal stable identity contract for an HHC event. The clinical payload is referenced,
 * never embedded in this transport-neutral metadata type.
 */
public record IntegrationEnvelope(
        UUID eventId,
        UUID correlationId,
        Scope scope,
        Instant receivedAt,
        String sourceSystem,
        String payloadReference,
        String payloadSha256,
        String contractVersion) {

    private static final Pattern SHA_256 = Pattern.compile("[a-f0-9]{64}");

    public IntegrationEnvelope {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(receivedAt, "receivedAt");
        sourceSystem = requireText(sourceSystem, "sourceSystem");
        payloadReference = requireText(payloadReference, "payloadReference");
        payloadSha256 = requireText(payloadSha256, "payloadSha256").toLowerCase();
        contractVersion = requireText(contractVersion, "contractVersion");
        if (!SHA_256.matcher(payloadSha256).matches()) {
            throw new IllegalArgumentException("payloadSha256 must be a lowercase SHA-256 value");
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }

    public record Scope(String tenantId, String organizationId, String facilityId) {
        public Scope {
            tenantId = requireText(tenantId, "tenantId");
            organizationId = requireText(organizationId, "organizationId");
            facilityId = requireText(facilityId, "facilityId");
        }
    }
}

