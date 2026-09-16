package io.hyperhealth.connect.controlplane.inventory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.hyperhealth.connect.controlplane.audit.InventoryAuditAction;
import io.hyperhealth.connect.controlplane.audit.InventoryAuditContext;
import io.hyperhealth.connect.controlplane.audit.InventoryAuditIntent;
import io.hyperhealth.connect.controlplane.audit.InventoryAuditJournal;
import io.hyperhealth.connect.controlplane.audit.InventoryAuditOutcome;
import io.hyperhealth.connect.controlplane.audit.InventoryAuditResourceType;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.ApplicationId;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.EndpointId;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.FacilityId;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.OrganizationId;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.TenantId;

/** PostgreSQL repository that applies tenant/facility predicates to every statement. */
public final class JdbcScopedEndpointRepository implements ScopedEndpointRepository {

    private static final String FIND_ENDPOINT = """
            SELECT endpoint_id, tenant_id, organization_id, facility_id, application_id,
                   display_name, lifecycle_state, row_version
              FROM platform_core.endpoint
             WHERE tenant_id = ? AND facility_id = ? AND endpoint_id = ?
               AND lifecycle_state <> 'DECOMMISSIONED'
            """;

    private static final String LIST_ENDPOINTS = """
            SELECT endpoint_id, tenant_id, organization_id, facility_id, application_id,
                   display_name, lifecycle_state, row_version
              FROM platform_core.endpoint
             WHERE tenant_id = ? AND facility_id = ?
               AND lifecycle_state <> 'DECOMMISSIONED'
               AND (?::uuid IS NULL OR application_id = ?::uuid)
               AND (?::text IS NULL OR lifecycle_state = ?::text)
               AND (?::uuid IS NULL OR endpoint_id > ?::uuid)
             ORDER BY endpoint_id
             LIMIT ?
            """;

    private static final String CLAIM_IDEMPOTENCY = """
            INSERT INTO platform_core.inventory_idempotency_record
                (tenant_id, facility_id, subject_digest, authorized_party, operation_code,
                 idempotency_key_digest, request_digest, expires_at)
            VALUES (?, ?, ?, ?, 'CREATE_ENDPOINT', ?, ?, ?)
            ON CONFLICT
                (tenant_id, facility_id, subject_digest, authorized_party,
                 operation_code, idempotency_key_digest)
            DO NOTHING
            """;

    private static final String FIND_IDEMPOTENCY = """
            SELECT request_digest, response_endpoint_id, response_organization_id,
                   response_application_id, response_display_name,
                   response_lifecycle_state, response_row_version
              FROM platform_core.inventory_idempotency_record
             WHERE tenant_id = ? AND facility_id = ? AND subject_digest = ?
               AND authorized_party = ? AND operation_code = 'CREATE_ENDPOINT'
               AND idempotency_key_digest = ? AND expires_at > transaction_timestamp()
            """;

    private static final String UPDATE_ENDPOINT = """
            UPDATE platform_core.endpoint
               SET display_name = COALESCE(?, display_name),
                   lifecycle_state = COALESCE(?::text, lifecycle_state),
                   decommissioned_at = CASE
                       WHEN ?::text = 'DECOMMISSIONED' THEN transaction_timestamp()
                       ELSE NULL
                   END,
                   updated_at = transaction_timestamp(),
                   row_version = row_version + 1
             WHERE tenant_id = ? AND facility_id = ? AND endpoint_id = ?
               AND row_version = ? AND lifecycle_state <> 'DECOMMISSIONED'
               AND (
                    ?::text IS NULL OR ?::text = lifecycle_state
                    OR (lifecycle_state = 'ACTIVE' AND ?::text IN ('SUSPENDED', 'DECOMMISSIONED'))
                    OR (lifecycle_state = 'SUSPENDED' AND ?::text IN ('ACTIVE', 'DECOMMISSIONED'))
               )
            RETURNING endpoint_id, tenant_id, organization_id, facility_id, application_id,
                      display_name, lifecycle_state, row_version
            """;

    private static final String INSERT_ENDPOINT = """
            INSERT INTO platform_core.endpoint
                (endpoint_id, tenant_id, organization_id, facility_id, application_id, display_name)
            VALUES (?, ?, ?, ?, ?, ?)
            RETURNING endpoint_id, tenant_id, organization_id, facility_id, application_id,
                      display_name, lifecycle_state, row_version
            """;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final InventoryAuditJournal auditJournal;

    public JdbcScopedEndpointRepository(DataSource dataSource, InventoryAuditJournal auditJournal) {
        DataSource requiredDataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.jdbcTemplate = new JdbcTemplate(requiredDataSource);
        this.jdbcTemplate.setQueryTimeout(5);
        this.transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(requiredDataSource));
        this.auditJournal = Objects.requireNonNull(auditJournal, "auditJournal");
    }

    @Override
    public Optional<ScopedEndpoint> findEndpoint(
            VerifiedFacilityScope scope,
            InventoryActor actor,
            InventoryAuditContext auditContext,
            EndpointId endpointId) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(auditContext, "auditContext");
        Objects.requireNonNull(endpointId, "endpointId");
        return Objects.requireNonNull(transactionTemplate.execute(status -> {
            Optional<ScopedEndpoint> endpoint = findEndpointRow(scope, endpointId);
            auditJournal.append(new InventoryAuditIntent(
                    scope,
                    auditContext,
                    actor,
                    InventoryAuditAction.ENDPOINT_READ,
                    endpoint.isPresent() ? InventoryAuditOutcome.SUCCESS : InventoryAuditOutcome.FAILURE,
                    endpoint.isPresent() ? null : "NOT_FOUND_OR_NOT_VISIBLE",
                    InventoryAuditResourceType.ENDPOINT,
                    endpointId.externalForm(),
                    "current-view=true"));
            return endpoint;
        }));
    }

    private Optional<ScopedEndpoint> findEndpointRow(VerifiedFacilityScope scope, EndpointId endpointId) {
        return jdbcTemplate.query(
                FIND_ENDPOINT,
                statement -> {
                    statement.setObject(1, scope.tenantId().value());
                    statement.setObject(2, scope.facilityId().value());
                    statement.setObject(3, endpointId.value());
                },
                resultSet -> resultSet.next() ? Optional.of(mapEndpoint(resultSet)) : Optional.empty());
    }

    @Override
    public EndpointPageSlice listEndpoints(
            VerifiedFacilityScope scope,
            InventoryActor actor,
            InventoryAuditContext auditContext,
            EndpointQuery query) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(auditContext, "auditContext");
        Objects.requireNonNull(query, "query");
        return Objects.requireNonNull(transactionTemplate.execute(status -> listEndpointsInTransaction(
                scope, actor, auditContext, query)));
    }

    private EndpointPageSlice listEndpointsInTransaction(
            VerifiedFacilityScope scope,
            InventoryActor actor,
            InventoryAuditContext auditContext,
            EndpointQuery query) {
        UUID applicationId = query.applicationId().map(ApplicationId::value).orElse(null);
        String lifecycleState = query.lifecycleState().map(Enum::name).orElse(null);
        UUID afterEndpointId = query.afterEndpointId().map(EndpointId::value).orElse(null);
        List<ScopedEndpoint> rows = jdbcTemplate.query(
                LIST_ENDPOINTS,
                statement -> {
                    statement.setObject(1, scope.tenantId().value());
                    statement.setObject(2, scope.facilityId().value());
                    setNullable(statement, 3, applicationId, Types.OTHER);
                    setNullable(statement, 4, applicationId, Types.OTHER);
                    setNullable(statement, 5, lifecycleState, Types.VARCHAR);
                    setNullable(statement, 6, lifecycleState, Types.VARCHAR);
                    setNullable(statement, 7, afterEndpointId, Types.OTHER);
                    setNullable(statement, 8, afterEndpointId, Types.OTHER);
                    statement.setInt(9, query.limit() + 1);
                },
                (resultSet, rowNumber) -> mapEndpoint(resultSet));
        boolean hasMore = rows.size() > query.limit();
        EndpointPageSlice page = new EndpointPageSlice(
                hasMore ? rows.subList(0, query.limit()) : rows, hasMore);
        auditJournal.append(new InventoryAuditIntent(
                scope,
                auditContext,
                actor,
                InventoryAuditAction.ENDPOINT_LIST,
                InventoryAuditOutcome.SUCCESS,
                null,
                InventoryAuditResourceType.ENDPOINT_COLLECTION,
                "application=" + query.applicationId().map(ApplicationId::externalForm).orElse("all")
                        + "|state=" + query.lifecycleState().map(Enum::name).orElse("all"),
                "limit=" + query.limit()
                        + "|after=" + query.afterEndpointId().isPresent()
                        + "|returned=" + page.items().size()
                        + "|hasMore=" + page.hasMore()));
        return page;
    }

    @Override
    public EndpointCreationResult createEndpoint(
            VerifiedFacilityScope scope,
            InventoryActor actor,
            InventoryAuditContext auditContext,
            EndpointIdempotency idempotency,
            ApplicationId applicationId,
            String displayName) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(auditContext, "auditContext");
        Objects.requireNonNull(idempotency, "idempotency");
        Objects.requireNonNull(applicationId, "applicationId");
        Objects.requireNonNull(displayName, "displayName");
        return Objects.requireNonNull(transactionTemplate.execute(status -> {
            deleteExpiredClaim(scope, actor, idempotency);
            int claimed = jdbcTemplate.update(
                    CLAIM_IDEMPOTENCY,
                    scope.tenantId().value(), scope.facilityId().value(),
                    idempotency.subjectDigest(), actor.authorizedParty(),
                    idempotency.keyDigest(),
                    idempotency.requestDigest(),
                    idempotency.expiresAt().atOffset(java.time.ZoneOffset.UTC));
            if (claimed == 0) {
                EndpointCreationResult replay = replayCreation(scope, actor, idempotency);
                appendCreationAudit(scope, actor, auditContext, replay.endpoint(), true);
                return replay;
            }
            OrganizationId organizationId = findApplicationOrganization(scope, applicationId)
                    .orElseThrow(InventoryResourceNotFoundException::new);
            EndpointId endpointId = EndpointId.newId();
            jdbcTemplate.update(
                    "INSERT INTO platform_core.resource_identity (resource_id, resource_type) VALUES (?, 'ENDPOINT')",
                    endpointId.value());
            ScopedEndpoint endpoint = jdbcTemplate.query(
                    INSERT_ENDPOINT,
                    statement -> {
                        statement.setObject(1, endpointId.value());
                        statement.setObject(2, scope.tenantId().value());
                        statement.setObject(3, organizationId.value());
                        statement.setObject(4, scope.facilityId().value());
                        statement.setObject(5, applicationId.value());
                        statement.setString(6, displayName);
                    },
                    resultSet -> {
                        if (!resultSet.next()) {
                            throw new IllegalStateException("Endpoint insert returned no row");
                        }
                        return mapEndpoint(resultSet);
                    });
            completeClaim(scope, actor, idempotency, endpoint);
            appendCreationAudit(scope, actor, auditContext, endpoint, false);
            return new EndpointCreationResult(endpoint, false);
        }));
    }

    @Override
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
        return Objects.requireNonNull(transactionTemplate.execute(status -> {
            String lifecycle = patch.lifecycleState().map(Enum::name).orElse(null);
            List<ScopedEndpoint> updated = jdbcTemplate.query(
                    UPDATE_ENDPOINT,
                    statement -> {
                        setNullable(statement, 1, patch.displayName().orElse(null), Types.VARCHAR);
                        setNullable(statement, 2, lifecycle, Types.VARCHAR);
                        setNullable(statement, 3, lifecycle, Types.VARCHAR);
                        statement.setObject(4, scope.tenantId().value());
                        statement.setObject(5, scope.facilityId().value());
                        statement.setObject(6, endpointId.value());
                        statement.setLong(7, expectedRowVersion);
                        for (int index = 8; index <= 11; index++) {
                            setNullable(statement, index, lifecycle, Types.VARCHAR);
                        }
                    },
                    (resultSet, rowNumber) -> mapEndpoint(resultSet));
            if (!updated.isEmpty()) {
                ScopedEndpoint endpoint = updated.getFirst();
                if (endpoint.lifecycleState() == InventoryLifecycleState.DECOMMISSIONED) {
                    jdbcTemplate.update("""
                            UPDATE platform_core.resource_identity
                               SET decommissioned_at = transaction_timestamp()
                             WHERE resource_id = ? AND resource_type = 'ENDPOINT'
                               AND decommissioned_at IS NULL
                            """, endpointId.value());
                }
                InventoryAuditAction action = endpoint.lifecycleState() == InventoryLifecycleState.DECOMMISSIONED
                        ? InventoryAuditAction.ENDPOINT_DECOMMISSION
                        : InventoryAuditAction.ENDPOINT_UPDATE;
                auditJournal.append(new InventoryAuditIntent(
                        scope,
                        auditContext,
                        actor,
                        action,
                        InventoryAuditOutcome.SUCCESS,
                        null,
                        InventoryAuditResourceType.ENDPOINT,
                        endpointId.externalForm(),
                        "displayNameChanged=" + patch.displayName().isPresent()
                                + "|lifecycleChanged=" + patch.lifecycleState().isPresent()
                                + "|expectedVersion=" + expectedRowVersion
                                + "|resultVersion=" + endpoint.rowVersion()));
                return endpoint;
            }
            Optional<ScopedEndpoint> current = findEndpointRow(scope, endpointId);
            if (current.isEmpty()) {
                throw new InventoryResourceNotFoundException();
            }
            if (current.orElseThrow().rowVersion() != expectedRowVersion) {
                throw new InventoryPreconditionFailedException();
            }
            throw new InventoryLifecycleConflictException();
        }));
    }

    private void deleteExpiredClaim(
            VerifiedFacilityScope scope, InventoryActor actor, EndpointIdempotency idempotency) {
        jdbcTemplate.update("""
                DELETE FROM platform_core.inventory_idempotency_record
                 WHERE tenant_id = ? AND facility_id = ? AND subject_digest = ?
                   AND authorized_party = ? AND operation_code = 'CREATE_ENDPOINT'
                   AND idempotency_key_digest = ? AND expires_at <= transaction_timestamp()
                """,
                scope.tenantId().value(), scope.facilityId().value(), idempotency.subjectDigest(),
                actor.authorizedParty(), idempotency.keyDigest());
    }

    private EndpointCreationResult replayCreation(
            VerifiedFacilityScope scope, InventoryActor actor, EndpointIdempotency idempotency) {
        return jdbcTemplate.query(
                FIND_IDEMPOTENCY,
                statement -> {
                    statement.setObject(1, scope.tenantId().value());
                    statement.setObject(2, scope.facilityId().value());
                    statement.setBytes(3, idempotency.subjectDigest());
                    statement.setString(4, actor.authorizedParty());
                    statement.setBytes(5, idempotency.keyDigest());
                },
                resultSet -> {
                    if (!resultSet.next()) {
                        throw new IllegalStateException("Idempotency claim disappeared during creation");
                    }
                    if (!Arrays.equals(resultSet.getBytes("request_digest"), idempotency.requestDigest())) {
                        throw new InventoryIdempotencyConflictException();
                    }
                    ScopedEndpoint endpoint = new ScopedEndpoint(
                            new EndpointId(resultSet.getObject("response_endpoint_id", UUID.class)),
                            scope.tenantId(),
                            new OrganizationId(resultSet.getObject("response_organization_id", UUID.class)),
                            scope.facilityId(),
                            new ApplicationId(resultSet.getObject("response_application_id", UUID.class)),
                            resultSet.getString("response_display_name"),
                            InventoryLifecycleState.valueOf(resultSet.getString("response_lifecycle_state")),
                            resultSet.getLong("response_row_version"));
                    return new EndpointCreationResult(endpoint, true);
                });
    }

    private Optional<OrganizationId> findApplicationOrganization(
            VerifiedFacilityScope scope, ApplicationId applicationId) {
        return jdbcTemplate.query("""
                SELECT organization_id FROM platform_core.application
                 WHERE tenant_id = ? AND facility_id = ? AND application_id = ?
                   AND lifecycle_state <> 'DECOMMISSIONED'
                """,
                statement -> {
                    statement.setObject(1, scope.tenantId().value());
                    statement.setObject(2, scope.facilityId().value());
                    statement.setObject(3, applicationId.value());
                },
                resultSet -> resultSet.next()
                        ? Optional.of(new OrganizationId(resultSet.getObject(1, UUID.class)))
                        : Optional.empty());
    }

    private void completeClaim(
            VerifiedFacilityScope scope,
            InventoryActor actor,
            EndpointIdempotency idempotency,
            ScopedEndpoint endpoint) {
        int updated = jdbcTemplate.update("""
                UPDATE platform_core.inventory_idempotency_record
                   SET response_endpoint_id = ?, response_organization_id = ?,
                       response_application_id = ?, response_display_name = ?,
                       response_lifecycle_state = ?, response_row_version = ?,
                       completed_at = transaction_timestamp()
                 WHERE tenant_id = ? AND facility_id = ? AND subject_digest = ?
                   AND authorized_party = ? AND operation_code = 'CREATE_ENDPOINT'
                   AND idempotency_key_digest = ? AND request_digest = ?
                """,
                endpoint.endpointId().value(), endpoint.organizationId().value(),
                endpoint.applicationId().value(), endpoint.displayName(),
                endpoint.lifecycleState().name(), endpoint.rowVersion(),
                scope.tenantId().value(), scope.facilityId().value(),
                idempotency.subjectDigest(), actor.authorizedParty(),
                idempotency.keyDigest(), idempotency.requestDigest());
        if (updated != 1) {
            throw new IllegalStateException("Idempotency claim could not be completed");
        }
    }

    private void appendCreationAudit(
            VerifiedFacilityScope scope,
            InventoryActor actor,
            InventoryAuditContext auditContext,
            ScopedEndpoint endpoint,
            boolean replayed) {
        auditJournal.append(new InventoryAuditIntent(
                scope,
                auditContext,
                actor,
                InventoryAuditAction.ENDPOINT_CREATE,
                InventoryAuditOutcome.SUCCESS,
                replayed ? "IDEMPOTENT_REPLAY" : null,
                InventoryAuditResourceType.ENDPOINT,
                endpoint.endpointId().externalForm(),
                "replayed=" + replayed + "|rowVersion=" + endpoint.rowVersion()));
    }

    private static ScopedEndpoint mapEndpoint(ResultSet resultSet) throws SQLException {
        return new ScopedEndpoint(
                new EndpointId(resultSet.getObject("endpoint_id", UUID.class)),
                new TenantId(resultSet.getObject("tenant_id", UUID.class)),
                new OrganizationId(resultSet.getObject("organization_id", UUID.class)),
                new FacilityId(resultSet.getObject("facility_id", UUID.class)),
                new ApplicationId(resultSet.getObject("application_id", UUID.class)),
                resultSet.getString("display_name"),
                InventoryLifecycleState.valueOf(resultSet.getString("lifecycle_state")),
                resultSet.getLong("row_version"));
    }

    private static void setNullable(
            java.sql.PreparedStatement statement, int index, Object value, int sqlType) throws SQLException {
        if (value == null) {
            statement.setNull(index, sqlType);
        } else {
            statement.setObject(index, value);
        }
    }
}
