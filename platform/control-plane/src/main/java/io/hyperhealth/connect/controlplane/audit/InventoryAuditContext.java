package io.hyperhealth.connect.controlplane.audit;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Per-request correlation context with conservative W3C traceparent acceptance. */
public record InventoryAuditContext(UUID correlationId, String traceId, Instant occurredAt) {

    private static final Pattern VERSION_ZERO_TRACEPARENT = Pattern.compile(
            "^00-([0-9a-f]{32})-[0-9a-f]{16}-[0-9a-f]{2}$");
    private static final String ZERO_TRACE_ID = "0".repeat(32);
    private static final SecureRandom RANDOM = new SecureRandom();

    public InventoryAuditContext {
        Objects.requireNonNull(correlationId, "correlationId");
        if (correlationId.equals(new UUID(0, 0))) {
            throw new IllegalArgumentException("correlationId must not be nil");
        }
        if (traceId == null
                || !traceId.matches("[0-9a-f]{32}")
                || traceId.equals(ZERO_TRACE_ID)) {
            throw new IllegalArgumentException("traceId must be a non-zero lowercase 16-byte hex value");
        }
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt").truncatedTo(ChronoUnit.MILLIS);
    }

    public static InventoryAuditContext create(String traceparent) {
        return create(traceparent, Clock.systemUTC());
    }

    static InventoryAuditContext create(String traceparent, Clock clock) {
        Objects.requireNonNull(clock, "clock");
        return new InventoryAuditContext(
                UUID.randomUUID(), acceptedTraceId(traceparent).orElseGet(InventoryAuditContext::newTraceId),
                Instant.now(clock));
    }

    private static java.util.Optional<String> acceptedTraceId(String traceparent) {
        if (traceparent == null) {
            return java.util.Optional.empty();
        }
        Matcher matcher = VERSION_ZERO_TRACEPARENT.matcher(traceparent);
        if (!matcher.matches() || matcher.group(1).equals(ZERO_TRACE_ID)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(matcher.group(1));
    }

    private static String newTraceId() {
        byte[] bytes = new byte[16];
        do {
            RANDOM.nextBytes(bytes);
        } while (java.util.Arrays.equals(bytes, new byte[16]));
        return HexFormat.of().formatHex(bytes);
    }
}
