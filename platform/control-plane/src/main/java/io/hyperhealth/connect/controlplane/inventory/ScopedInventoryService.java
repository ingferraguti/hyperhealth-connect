package io.hyperhealth.connect.controlplane.inventory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import io.hyperhealth.connect.controlplane.audit.InventoryAuditContext;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.ApplicationId;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.EndpointId;

/** Application service that keeps verified scope mandatory at every public boundary. */
public final class ScopedInventoryService {

    private static final Duration DEFAULT_IDEMPOTENCY_RETENTION = Duration.ofHours(24);

    private final ScopedEndpointRepository endpointRepository;
    private final Clock clock;
    private final Duration idempotencyRetention;

    public ScopedInventoryService(ScopedEndpointRepository endpointRepository) {
        this(endpointRepository, Clock.systemUTC(), DEFAULT_IDEMPOTENCY_RETENTION);
    }

    public ScopedInventoryService(
            ScopedEndpointRepository endpointRepository,
            Clock clock,
            Duration idempotencyRetention) {
        this.endpointRepository = Objects.requireNonNull(endpointRepository, "endpointRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.idempotencyRetention = Objects.requireNonNull(idempotencyRetention, "idempotencyRetention");
        if (idempotencyRetention.compareTo(Duration.ofHours(1)) < 0
                || idempotencyRetention.compareTo(Duration.ofDays(7)) > 0) {
            throw new IllegalArgumentException("idempotencyRetention must be between 1h and 7d");
        }
    }

    public ScopedEndpoint getEndpoint(
            VerifiedFacilityScope scope,
            InventoryActor actor,
            InventoryAuditContext auditContext,
            EndpointId endpointId) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(auditContext, "auditContext");
        Objects.requireNonNull(endpointId, "endpointId");
        return endpointRepository
                .findEndpoint(scope, actor, auditContext, endpointId)
                .orElseThrow(InventoryResourceNotFoundException::new);
    }

    public EndpointPageSlice listEndpoints(
            VerifiedFacilityScope scope,
            InventoryActor actor,
            InventoryAuditContext auditContext,
            EndpointQuery query) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(auditContext, "auditContext");
        Objects.requireNonNull(query, "query");
        return endpointRepository.listEndpoints(scope, actor, auditContext, query);
    }

    public EndpointCreationResult createEndpoint(
            VerifiedFacilityScope scope,
            InventoryActor actor,
            InventoryAuditContext auditContext,
            UUID idempotencyKey,
            ApplicationId applicationId,
            String displayName) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(auditContext, "auditContext");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(applicationId, "applicationId");
        String normalizedName = normalizeDisplayName(displayName);
        EndpointIdempotency idempotency = new EndpointIdempotency(
                digest("hhc:subject:v1", actor.subject()),
                digest("hhc:idempotency-key:v1", idempotencyKey),
                requestDigest(applicationId, normalizedName),
                Instant.now(clock).plus(idempotencyRetention));
        return endpointRepository.createEndpoint(
                scope, actor, auditContext, idempotency, applicationId, normalizedName);
    }

    public ScopedEndpoint updateEndpoint(
            VerifiedFacilityScope scope,
            InventoryActor actor,
            InventoryAuditContext auditContext,
            EndpointId endpointId,
            long expectedRowVersion,
            EndpointPatch patch) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(auditContext, "auditContext");
        Objects.requireNonNull(endpointId, "endpointId");
        Objects.requireNonNull(patch, "patch");
        if (expectedRowVersion < 0) {
            throw new InvalidInventoryRequestException("row version must not be negative");
        }
        EndpointPatch normalizedPatch = new EndpointPatch(
                patch.displayName().map(ScopedInventoryService::normalizeDisplayName),
                patch.lifecycleState());
        return endpointRepository.updateEndpoint(
                scope, actor, auditContext, endpointId, expectedRowVersion, normalizedPatch);
    }

    static String normalizeDisplayName(String value) {
        if (value == null) {
            throw new InvalidInventoryRequestException("displayName is required");
        }
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.codePointCount(0, normalized.length()) > 256) {
            throw new InvalidInventoryRequestException("displayName must contain between 1 and 256 characters");
        }
        if (normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new InvalidInventoryRequestException("displayName contains a disallowed control character");
        }
        return normalized;
    }

    private static byte[] requestDigest(ApplicationId applicationId, String displayName) {
        MessageDigest digest = sha256();
        update(digest, "hhc:create-endpoint:v1");
        update(digest, applicationId.value());
        update(digest, displayName);
        return digest.digest();
    }

    private static byte[] digest(String domain, String value) {
        MessageDigest digest = sha256();
        update(digest, domain);
        update(digest, value);
        return digest.digest();
    }

    private static byte[] digest(String domain, UUID value) {
        MessageDigest digest = sha256();
        update(digest, domain);
        update(digest, value);
        return digest.digest();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("The Java runtime does not provide SHA-256", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static void update(MessageDigest digest, UUID value) {
        digest.update(ByteBuffer.allocate(Long.BYTES * 2)
                .putLong(value.getMostSignificantBits())
                .putLong(value.getLeastSignificantBits())
                .array());
    }
}
