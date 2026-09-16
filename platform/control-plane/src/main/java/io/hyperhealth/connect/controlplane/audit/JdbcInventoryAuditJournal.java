package io.hyperhealth.connect.controlplane.audit;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.hyperhealth.connect.controlplane.inventory.VerifiedFacilityScope;

/** PostgreSQL-backed, per-scope HMAC chain. Raw actor and resource identifiers are never stored. */
public final class JdbcInventoryAuditJournal implements InventoryAuditJournal {

    private static final byte[] GENESIS_HASH = new byte[32];
    private static final String SCHEMA_VERSION = "1.0.0";
    private static final String POLICY_ID = "inventory-endpoint-rbac";
    private static final String POLICY_VERSION = "0.2.0";
    private static final String DESTINATION = "hhc-control-plane";

    private static final String SELECT_HEAD_FOR_UPDATE = """
            SELECT last_sequence, last_record_hash
              FROM platform_core.inventory_audit_chain_head
             WHERE tenant_id = ? AND facility_id = ?
             FOR UPDATE
            """;

    private static final String SELECT_HEAD_FOR_SHARE = """
            SELECT last_sequence, last_record_hash
              FROM platform_core.inventory_audit_chain_head
             WHERE tenant_id = ? AND facility_id = ?
             FOR SHARE
            """;

    private static final String SELECT_EVENTS = """
            SELECT event_id, tenant_id, facility_id, scope_sequence,
                   occurred_at, recorded_at, action_code, outcome_code, reason_code,
                   actor_type, actor_id_hash, authorized_party, correlation_id, trace_id,
                   resource_type, resource_id_hash, detail_digest, policy_id, policy_version,
                   policy_digest, artifact_digest, source_id, destination_id, schema_version,
                   integrity_key_id, previous_record_hash, record_hash
              FROM platform_core.inventory_audit_event
             WHERE tenant_id = ? AND facility_id = ?
             ORDER BY scope_sequence
            """;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final byte[] integrityKey;
    private final byte[] pseudonymizationKey;
    private final String integrityKeyId;
    private final byte[] policyDigest;
    private final byte[] artifactDigest;

    public JdbcInventoryAuditJournal(
            DataSource dataSource,
            Clock clock,
            byte[] integrityKey,
            byte[] pseudonymizationKey,
            String integrityKeyId,
            byte[] policyDigest,
            byte[] artifactDigest) {
        DataSource requiredDataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.jdbcTemplate = new JdbcTemplate(requiredDataSource);
        this.jdbcTemplate.setQueryTimeout(5);
        this.transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(requiredDataSource));
        this.clock = Objects.requireNonNull(clock, "clock");
        this.integrityKey = requireKey(integrityKey, "integrityKey");
        this.pseudonymizationKey = requireKey(pseudonymizationKey, "pseudonymizationKey");
        this.integrityKeyId = requireKeyId(integrityKeyId);
        this.policyDigest = requireDigest(policyDigest, "policyDigest");
        this.artifactDigest = requireDigest(artifactDigest, "artifactDigest");
    }

    @Override
    public void append(InventoryAuditIntent intent) {
        Objects.requireNonNull(intent, "intent");
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Inventory audit append requires an active database transaction");
        }
        VerifiedFacilityScope scope = intent.scope();
        jdbcTemplate.update("""
                INSERT INTO platform_core.inventory_audit_chain_head
                    (tenant_id, facility_id, last_record_hash)
                VALUES (?, ?, ?)
                ON CONFLICT (tenant_id, facility_id) DO NOTHING
                """, scope.tenantId().value(), scope.facilityId().value(), GENESIS_HASH);

        ChainHead head = jdbcTemplate.query(
                SELECT_HEAD_FOR_UPDATE,
                statement -> {
                    statement.setObject(1, scope.tenantId().value());
                    statement.setObject(2, scope.facilityId().value());
                },
                resultSet -> {
                    if (!resultSet.next()) {
                        throw new IllegalStateException("Inventory audit chain head is unavailable");
                    }
                    return new ChainHead(resultSet.getLong(1), resultSet.getBytes(2));
                });

        long sequence = Math.addExact(head.sequence(), 1);
        Instant recordedAt = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
        if (recordedAt.isBefore(intent.context().occurredAt())) {
            recordedAt = intent.context().occurredAt();
        }
        byte[] actorHash = pseudonymize("hhc:audit:actor:v1", intent.actor().subject());
        byte[] resourceHash = pseudonymize("hhc:audit:resource:v1", intent.resourceKey());
        byte[] detailDigest = sha256("hhc:audit:detail:v1", intent.detailFingerprint());
        AuditRow row = new AuditRow(
                UUID.randomUUID(),
                scope.tenantId().value(),
                scope.facilityId().value(),
                sequence,
                intent.context().occurredAt(),
                recordedAt,
                intent.action().name(),
                intent.outcome().name(),
                intent.reasonCode(),
                intent.actor().actorType().name(),
                actorHash,
                intent.actor().authorizedParty(),
                intent.context().correlationId(),
                intent.context().traceId(),
                intent.resourceType().name(),
                resourceHash,
                detailDigest,
                POLICY_ID,
                POLICY_VERSION,
                policyDigest,
                artifactDigest,
                intent.actor().authorizedParty(),
                DESTINATION,
                SCHEMA_VERSION,
                integrityKeyId,
                head.hash(),
                null);
        byte[] recordHash = recordHash(row);
        insert(row.withRecordHash(recordHash));
        int updated = jdbcTemplate.update("""
                UPDATE platform_core.inventory_audit_chain_head
                   SET last_sequence = ?, last_record_hash = ?, updated_at = ?
                 WHERE tenant_id = ? AND facility_id = ? AND last_sequence = ?
                """,
                sequence,
                recordHash,
                recordedAt.atOffset(ZoneOffset.UTC),
                scope.tenantId().value(),
                scope.facilityId().value(),
                head.sequence());
        if (updated != 1) {
            throw new IllegalStateException("Inventory audit chain head did not advance atomically");
        }
    }

    @Override
    public AuditChainVerification verify(VerifiedFacilityScope scope) {
        Objects.requireNonNull(scope, "scope");
        return Objects.requireNonNull(transactionTemplate.execute(status -> verifyInTransaction(scope)));
    }

    private AuditChainVerification verifyInTransaction(VerifiedFacilityScope scope) {
        ChainHead head = jdbcTemplate.query(
                SELECT_HEAD_FOR_SHARE,
                statement -> {
                    statement.setObject(1, scope.tenantId().value());
                    statement.setObject(2, scope.facilityId().value());
                },
                resultSet -> resultSet.next()
                        ? new ChainHead(resultSet.getLong(1), resultSet.getBytes(2))
                        : new ChainHead(0, GENESIS_HASH));
        List<AuditRow> events = jdbcTemplate.query(
                SELECT_EVENTS,
                statement -> {
                    statement.setObject(1, scope.tenantId().value());
                    statement.setObject(2, scope.facilityId().value());
                },
                (resultSet, rowNumber) -> mapRow(resultSet));
        long expectedSequence = 1;
        byte[] expectedPrevious = GENESIS_HASH;
        for (AuditRow event : events) {
            if (event.sequence() != expectedSequence
                    || !MessageDigest.isEqual(event.previousRecordHash(), expectedPrevious)
                    || !event.integrityKeyId().equals(integrityKeyId)
                    || !MessageDigest.isEqual(recordHash(event.withRecordHash(null)), event.recordHash())) {
                return AuditChainVerification.invalid(events.size(), expectedSequence);
            }
            expectedPrevious = event.recordHash();
            expectedSequence++;
        }
        if (head.sequence() != events.size() || !MessageDigest.isEqual(head.hash(), expectedPrevious)) {
            return AuditChainVerification.invalid(events.size(), expectedSequence);
        }
        return AuditChainVerification.valid(events.size());
    }

    private void insert(AuditRow row) {
        jdbcTemplate.update("""
                INSERT INTO platform_core.inventory_audit_event
                    (event_id, tenant_id, facility_id, scope_sequence,
                     occurred_at, recorded_at, action_code, outcome_code, reason_code,
                     actor_type, actor_id_hash, authorized_party, correlation_id, trace_id,
                     resource_type, resource_id_hash, detail_digest, policy_id, policy_version,
                     policy_digest, artifact_digest, source_id, destination_id, schema_version,
                     integrity_key_id, previous_record_hash, record_hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                row.eventId(), row.tenantId(), row.facilityId(), row.sequence(),
                row.occurredAt().atOffset(ZoneOffset.UTC), row.recordedAt().atOffset(ZoneOffset.UTC),
                row.action(), row.outcome(), row.reasonCode(), row.actorType(), row.actorIdHash(),
                row.authorizedParty(), row.correlationId(), row.traceId(), row.resourceType(),
                row.resourceIdHash(), row.detailDigest(), row.policyId(), row.policyVersion(),
                row.policyDigest(), row.artifactDigest(), row.sourceId(), row.destinationId(),
                row.schemaVersion(), row.integrityKeyId(), row.previousRecordHash(), row.recordHash());
    }

    private byte[] recordHash(AuditRow row) {
        List<byte[]> fields = new ArrayList<>();
        fields.add(uuidBytes(row.eventId()));
        fields.add(uuidBytes(row.tenantId()));
        fields.add(uuidBytes(row.facilityId()));
        fields.add(longBytes(row.sequence()));
        fields.add(longBytes(row.occurredAt().toEpochMilli()));
        fields.add(longBytes(row.recordedAt().toEpochMilli()));
        fields.add(bytes(row.action()));
        fields.add(bytes(row.outcome()));
        fields.add(bytes(row.reasonCode() == null ? "" : row.reasonCode()));
        fields.add(bytes(row.actorType()));
        fields.add(row.actorIdHash());
        fields.add(bytes(row.authorizedParty()));
        fields.add(uuidBytes(row.correlationId()));
        fields.add(bytes(row.traceId()));
        fields.add(bytes(row.resourceType()));
        fields.add(row.resourceIdHash());
        fields.add(row.detailDigest());
        fields.add(bytes(row.policyId()));
        fields.add(bytes(row.policyVersion()));
        fields.add(row.policyDigest());
        fields.add(row.artifactDigest());
        fields.add(bytes(row.sourceId()));
        fields.add(bytes(row.destinationId()));
        fields.add(bytes(row.schemaVersion()));
        fields.add(bytes(row.integrityKeyId()));
        fields.add(row.previousRecordHash());
        return hmac(integrityKey, "hhc:audit:record:v1", fields.toArray(byte[][]::new));
    }

    private byte[] pseudonymize(String domain, String value) {
        return hmac(pseudonymizationKey, domain, bytes(value));
    }

    private static byte[] sha256(String domain, String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, bytes(domain));
            update(digest, bytes(value));
            return digest.digest();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("The Java runtime does not provide SHA-256", exception);
        }
    }

    private static byte[] hmac(byte[] key, String domain, byte[]... fields) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            update(mac, bytes(domain));
            for (byte[] field : fields) {
                update(mac, field);
            }
            return mac.doFinal();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("The Java runtime does not provide HMAC-SHA-256", exception);
        }
    }

    private static void update(MessageDigest digest, byte[] value) {
        digest.update(intBytes(value.length));
        digest.update(value);
    }

    private static void update(Mac mac, byte[] value) {
        mac.update(intBytes(value.length));
        mac.update(value);
    }

    private static byte[] intBytes(int value) {
        return ByteBuffer.allocate(Integer.BYTES).putInt(value).array();
    }

    private static byte[] longBytes(long value) {
        return ByteBuffer.allocate(Long.BYTES).putLong(value).array();
    }

    private static byte[] uuidBytes(UUID value) {
        return ByteBuffer.allocate(Long.BYTES * 2)
                .putLong(value.getMostSignificantBits())
                .putLong(value.getLeastSignificantBits())
                .array();
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] requireKey(byte[] value, String name) {
        Objects.requireNonNull(value, name);
        if (value.length < 32) {
            throw new IllegalArgumentException(name + " must contain at least 256 bits");
        }
        return value.clone();
    }

    private static byte[] requireDigest(byte[] value, String name) {
        Objects.requireNonNull(value, name);
        if (value.length != 32) {
            throw new IllegalArgumentException(name + " must be a SHA-256 digest");
        }
        return value.clone();
    }

    private static String requireKeyId(String value) {
        if (value == null || !value.matches("[A-Za-z0-9._:-]{1,128}")) {
            throw new IllegalArgumentException("integrityKeyId is not canonical");
        }
        return value;
    }

    private static AuditRow mapRow(ResultSet resultSet) throws SQLException {
        return new AuditRow(
                resultSet.getObject("event_id", UUID.class),
                resultSet.getObject("tenant_id", UUID.class),
                resultSet.getObject("facility_id", UUID.class),
                resultSet.getLong("scope_sequence"),
                resultSet.getTimestamp("occurred_at").toInstant(),
                resultSet.getTimestamp("recorded_at").toInstant(),
                resultSet.getString("action_code"),
                resultSet.getString("outcome_code"),
                resultSet.getString("reason_code"),
                resultSet.getString("actor_type"),
                resultSet.getBytes("actor_id_hash"),
                resultSet.getString("authorized_party"),
                resultSet.getObject("correlation_id", UUID.class),
                resultSet.getString("trace_id"),
                resultSet.getString("resource_type"),
                resultSet.getBytes("resource_id_hash"),
                resultSet.getBytes("detail_digest"),
                resultSet.getString("policy_id"),
                resultSet.getString("policy_version"),
                resultSet.getBytes("policy_digest"),
                resultSet.getBytes("artifact_digest"),
                resultSet.getString("source_id"),
                resultSet.getString("destination_id"),
                resultSet.getString("schema_version"),
                resultSet.getString("integrity_key_id"),
                resultSet.getBytes("previous_record_hash"),
                resultSet.getBytes("record_hash"));
    }

    private record ChainHead(long sequence, byte[] hash) {
        private ChainHead {
            hash = Arrays.copyOf(hash, hash.length);
        }

        @Override
        public byte[] hash() {
            return Arrays.copyOf(hash, hash.length);
        }
    }

    private record AuditRow(
            UUID eventId,
            UUID tenantId,
            UUID facilityId,
            long sequence,
            Instant occurredAt,
            Instant recordedAt,
            String action,
            String outcome,
            String reasonCode,
            String actorType,
            byte[] actorIdHash,
            String authorizedParty,
            UUID correlationId,
            String traceId,
            String resourceType,
            byte[] resourceIdHash,
            byte[] detailDigest,
            String policyId,
            String policyVersion,
            byte[] policyDigest,
            byte[] artifactDigest,
            String sourceId,
            String destinationId,
            String schemaVersion,
            String integrityKeyId,
            byte[] previousRecordHash,
            byte[] recordHash) {

        AuditRow withRecordHash(byte[] value) {
            return new AuditRow(
                    eventId, tenantId, facilityId, sequence, occurredAt, recordedAt, action, outcome,
                    reasonCode, actorType, actorIdHash, authorizedParty, correlationId, traceId,
                    resourceType, resourceIdHash, detailDigest, policyId, policyVersion, policyDigest,
                    artifactDigest, sourceId, destinationId, schemaVersion, integrityKeyId,
                    previousRecordHash, value);
        }
    }
}
