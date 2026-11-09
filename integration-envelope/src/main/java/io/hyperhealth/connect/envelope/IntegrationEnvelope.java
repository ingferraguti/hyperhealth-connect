package io.hyperhealth.connect.envelope;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Immutable identity and routing core of the HHC Integration Envelope v1.
 *
 * <p>The envelope contains technical identifiers only. Clinical identifiers and payload bytes do
 * not belong in this type. Raw references and checksums are deliberately introduced by later
 * WP1-02 activities at the durability boundary.</p>
 */
public record IntegrationEnvelope(
        String envelopeVersion,
        MessageId messageId,
        CorrelationId correlationId,
        Optional<MessageId> causationId,
        Scope scope,
        Processing processing,
        Timing time) {

    public static final String VERSION_1 = "hhc-envelope/v1";

    private static final String UUID_URN_PREFIX = "urn:uuid:";
    private static final Pattern SAFE_CORRELATION =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._~:/@+-]{0,127}");
    private static final Pattern FLOW_ID =
            Pattern.compile("flow-[a-z0-9](?:[a-z0-9._-]{0,62}[a-z0-9])?");
    private static final Pattern IMMUTABLE_VERSION =
            Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9._+:-]{0,126}[A-Za-z0-9])?");
    private static final Set<String> FLOATING_VERSION_ALIASES =
            Set.of("latest", "current", "head", "snapshot");

    public IntegrationEnvelope {
        if (!VERSION_1.equals(envelopeVersion)) {
            throw new IllegalArgumentException("Unsupported envelopeVersion: " + envelopeVersion);
        }
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(causationId, "causationId");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(processing, "processing");
        Objects.requireNonNull(time, "time");
        if (causationId.filter(messageId::equals).isPresent()) {
            throw new IllegalArgumentException("causationId must not reference the same message");
        }
    }

    public static IntegrationEnvelope root(
            MessageId messageId,
            CorrelationId correlationId,
            Scope scope,
            Processing processing,
            Timing time) {
        return new IntegrationEnvelope(
                VERSION_1,
                messageId,
                correlationId,
                Optional.empty(),
                scope,
                processing,
                time);
    }

    public static IntegrationEnvelope causedBy(
            MessageId messageId,
            CorrelationId correlationId,
            MessageId causationId,
            Scope scope,
            Processing processing,
            Timing time) {
        return new IntegrationEnvelope(
                VERSION_1,
                messageId,
                correlationId,
                Optional.of(Objects.requireNonNull(causationId, "causationId")),
                scope,
                processing,
                time);
    }

    /** RFC 9562 UUID expressed in the canonical lower-case UUID URN form. */
    public record MessageId(UUID value) {

        public MessageId {
            value = requireMessageUuid(value);
        }

        public static MessageId parse(String wireValue) {
            Objects.requireNonNull(wireValue, "wireValue");
            if (!wireValue.startsWith(UUID_URN_PREFIX)) {
                throw new IllegalArgumentException("message ID must use the urn:uuid form");
            }
            String rawUuid = wireValue.substring(UUID_URN_PREFIX.length());
            UUID parsed = parseCanonicalUuid(rawUuid, "message ID");
            return new MessageId(parsed);
        }

        public String wireValue() {
            return UUID_URN_PREFIX + value;
        }

        @Override
        public String toString() {
            return wireValue();
        }
    }

    /** Bounded, log-safe technical correlation token. It is not a patient or business ID. */
    public record CorrelationId(String value) {

        public CorrelationId {
            value = requireMatching(value, "correlationId", SAFE_CORRELATION);
        }

        public static CorrelationId from(MessageId messageId) {
            return new CorrelationId(Objects.requireNonNull(messageId, "messageId").wireValue());
        }

        @Override
        public String toString() {
            return value;
        }
    }

    /** Tenant and Facility are authoritative immutable control-plane identifiers. */
    public record Scope(String tenantId, String facilityId) {

        public Scope {
            tenantId = requireScopedId(tenantId, "tenantId", "t-");
            facilityId = requireScopedId(facilityId, "facilityId", "f-");
        }
    }

    /** Logical Flow plus the exact immutable release used to process the message. */
    public record Processing(String flowId, String flowVersion) {

        public Processing {
            flowId = requireMatching(flowId, "flowId", FLOW_ID);
            flowVersion = requireMatching(flowVersion, "flowVersion", IMMUTABLE_VERSION);
            if (FLOATING_VERSION_ALIASES.contains(flowVersion.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("flowVersion must identify an immutable release");
            }
        }
    }

    /** Technical receive time at the HHC trust boundary, always represented as an Instant. */
    public record Timing(Instant receivedAt) {

        public Timing {
            Objects.requireNonNull(receivedAt, "receivedAt");
        }
    }

    private static UUID requireMessageUuid(UUID value) {
        Objects.requireNonNull(value, "value");
        requireNonNil(value, "message ID");
        if (value.variant() != 2 || (value.version() != 4 && value.version() != 7)) {
            throw new IllegalArgumentException("message ID must be an RFC 9562 UUIDv4 or UUIDv7");
        }
        return value;
    }

    private static String requireScopedId(
            String externalForm, String name, String expectedPrefix) {
        Objects.requireNonNull(externalForm, name);
        if (!externalForm.startsWith(expectedPrefix)) {
            throw new IllegalArgumentException(name + " must start with " + expectedPrefix);
        }
        UUID value = parseCanonicalUuid(externalForm.substring(expectedPrefix.length()), name);
        requireNonNil(value, name);
        return externalForm;
    }

    private static UUID parseCanonicalUuid(String value, String name) {
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equals(value)) {
                throw new IllegalArgumentException(name + " is not in canonical lower-case UUID form");
            }
            return parsed;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    name + " is not in canonical lower-case UUID form", exception);
        }
    }

    private static void requireNonNil(UUID value, String name) {
        if (value.getMostSignificantBits() == 0L && value.getLeastSignificantBits() == 0L) {
            throw new IllegalArgumentException(name + " must not use the nil UUID");
        }
    }

    private static String requireMatching(String value, String name, Pattern pattern) {
        Objects.requireNonNull(value, name);
        if (!pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " is not a canonical bounded technical ID");
        }
        return value;
    }
}
