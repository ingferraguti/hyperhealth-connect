package io.hyperhealth.connect.controlplane.inventory.api;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import io.hyperhealth.connect.controlplane.inventory.EndpointQuery;
import io.hyperhealth.connect.controlplane.inventory.InventoryActor;
import io.hyperhealth.connect.controlplane.inventory.InvalidInventoryRequestException;
import io.hyperhealth.connect.controlplane.inventory.VerifiedFacilityScope;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.EndpointId;

/** HMAC-authenticated cursor bound to actor, scope, query, sort and a short expiry. */
public final class InventoryCursorCodec {

    private static final byte FORMAT_VERSION = 1;
    private static final int PAYLOAD_BYTES = 1 + Long.BYTES + (Long.BYTES * 2);
    private static final int MAXIMUM_TOKEN_LENGTH = 512;

    private final byte[] signingKey;
    private final Duration timeToLive;
    private final Clock clock;

    public InventoryCursorCodec(byte[] signingKey, Duration timeToLive) {
        this(signingKey, timeToLive, Clock.systemUTC());
    }

    InventoryCursorCodec(byte[] signingKey, Duration timeToLive, Clock clock) {
        Objects.requireNonNull(signingKey, "signingKey");
        if (signingKey.length < 32) {
            throw new IllegalArgumentException("cursor signing key must contain at least 256 bits");
        }
        this.signingKey = Arrays.copyOf(signingKey, signingKey.length);
        this.timeToLive = Objects.requireNonNull(timeToLive, "timeToLive");
        if (timeToLive.compareTo(Duration.ofMinutes(1)) < 0
                || timeToLive.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException("cursor TTL must be between 1m and 1h");
        }
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public String encode(
            InventoryActor actor,
            VerifiedFacilityScope scope,
            EndpointQuery query,
            EndpointId lastEndpointId) {
        Objects.requireNonNull(lastEndpointId, "lastEndpointId");
        byte[] payload = ByteBuffer.allocate(PAYLOAD_BYTES)
                .put(FORMAT_VERSION)
                .putLong(Instant.now(clock).plus(timeToLive).getEpochSecond())
                .putLong(lastEndpointId.value().getMostSignificantBits())
                .putLong(lastEndpointId.value().getLeastSignificantBits())
                .array();
        byte[] signature = sign(payload, context(actor, scope, query));
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString(payload) + "." + encoder.encodeToString(signature);
    }

    public Optional<EndpointId> decode(
            String token,
            InventoryActor actor,
            VerifiedFacilityScope scope,
            EndpointQuery queryWithoutCursor) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        if (token.length() > MAXIMUM_TOKEN_LENGTH) {
            throw invalidCursor();
        }
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 2) {
                throw invalidCursor();
            }
            Base64.Decoder decoder = Base64.getUrlDecoder();
            byte[] payload = decoder.decode(parts[0]);
            byte[] suppliedSignature = decoder.decode(parts[1]);
            if (payload.length != PAYLOAD_BYTES
                    || !MessageDigest.isEqual(
                            suppliedSignature, sign(payload, context(actor, scope, queryWithoutCursor)))) {
                throw invalidCursor();
            }
            ByteBuffer fields = ByteBuffer.wrap(payload);
            if (fields.get() != FORMAT_VERSION) {
                throw invalidCursor();
            }
            long expiresAt = fields.getLong();
            if (!Instant.ofEpochSecond(expiresAt).isAfter(Instant.now(clock))) {
                throw invalidCursor();
            }
            return Optional.of(new EndpointId(new UUID(fields.getLong(), fields.getLong())));
        } catch (IllegalArgumentException exception) {
            throw invalidCursor();
        }
    }

    private byte[] sign(byte[] payload, byte[] context) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            mac.update(payload);
            mac.update(context);
            return mac.doFinal();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("The Java runtime cannot create an HMAC-SHA-256 cursor", exception);
        }
    }

    private static byte[] context(
            InventoryActor actor, VerifiedFacilityScope scope, EndpointQuery query) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(query, "query");
        String canonical = String.join("\u0000",
                "hhc:endpoint-cursor:v1",
                actor.subject(),
                actor.authorizedParty(),
                scope.tenantId().externalForm(),
                scope.facilityId().externalForm(),
                query.applicationId().map(id -> id.externalForm()).orElse("-"),
                query.lifecycleState().map(Enum::name).orElse("-"),
                Integer.toString(query.limit()),
                "endpointId:asc");
        return canonical.getBytes(StandardCharsets.UTF_8);
    }

    private static InvalidInventoryRequestException invalidCursor() {
        return new InvalidInventoryRequestException("cursor is invalid, expired, or does not match the query");
    }
}
