package io.hyperhealth.connect.controlplane.secret;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import io.hyperhealth.connect.controlplane.inventory.InventoryId.EndpointId;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.FacilityId;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.TenantId;
import io.hyperhealth.connect.controlplane.inventory.VerifiedFacilityScope;

/** PostgreSQL implementation that derives ancestry and bindings from already-scoped rows. */
public final class JdbcSecretReferenceRepository implements SecretReferenceRepository {

    private static final String REGISTER = """
            INSERT INTO platform_core.secret_reference
                (secret_reference_id, tenant_id, organization_id, facility_id,
                 provider_kind, backend_binding_id, purpose)
            SELECT ?, facility.tenant_id, facility.organization_id, facility.facility_id, ?, ?, ?
              FROM platform_core.facility AS facility
             WHERE facility.tenant_id = ?
               AND facility.facility_id = ?
               AND facility.lifecycle_state <> 'DECOMMISSIONED'
            """;

    private static final String FIND_USABLE = """
            SELECT secret_reference_id,
                   tenant_id,
                   facility_id,
                   provider_kind,
                   backend_binding_id,
                   purpose,
                   reference_state,
                   row_version
              FROM platform_core.secret_reference
             WHERE tenant_id = ?
               AND facility_id = ?
               AND secret_reference_id = ?
               AND reference_state IN ('ACTIVE', 'ROTATING')
            """;

    private static final String BIND_TO_ENDPOINT = """
            INSERT INTO platform_core.endpoint_secret_binding
                (tenant_id, organization_id, facility_id, application_id, endpoint_id,
                 secret_reference_id, purpose)
            SELECT endpoint.tenant_id,
                   endpoint.organization_id,
                   endpoint.facility_id,
                   endpoint.application_id,
                   endpoint.endpoint_id,
                   reference.secret_reference_id,
                   reference.purpose
              FROM platform_core.endpoint AS endpoint
              JOIN platform_core.secret_reference AS reference
                ON reference.tenant_id = endpoint.tenant_id
               AND reference.organization_id = endpoint.organization_id
               AND reference.facility_id = endpoint.facility_id
             WHERE endpoint.tenant_id = ?
               AND endpoint.facility_id = ?
               AND endpoint.endpoint_id = ?
               AND endpoint.lifecycle_state <> 'DECOMMISSIONED'
               AND reference.secret_reference_id = ?
               AND reference.reference_state IN ('ACTIVE', 'ROTATING')
            """;

    private static final String EXPORT_ENDPOINT_REFERENCES = """
            SELECT reference.secret_reference_id,
                   reference.provider_kind,
                   reference.purpose,
                   reference.reference_state,
                   reference.row_version
              FROM platform_core.endpoint_secret_binding AS binding
              JOIN platform_core.endpoint AS endpoint
                ON endpoint.tenant_id = binding.tenant_id
               AND endpoint.organization_id = binding.organization_id
               AND endpoint.facility_id = binding.facility_id
               AND endpoint.application_id = binding.application_id
               AND endpoint.endpoint_id = binding.endpoint_id
              JOIN platform_core.secret_reference AS reference
                ON reference.tenant_id = binding.tenant_id
               AND reference.organization_id = binding.organization_id
               AND reference.facility_id = binding.facility_id
               AND reference.secret_reference_id = binding.secret_reference_id
               AND reference.purpose = binding.purpose
             WHERE binding.tenant_id = ?
               AND binding.facility_id = ?
               AND binding.endpoint_id = ?
               AND binding.retired_at IS NULL
               AND endpoint.lifecycle_state <> 'DECOMMISSIONED'
               AND reference.reference_state IN ('ACTIVE', 'ROTATING')
             ORDER BY reference.purpose, reference.secret_reference_id
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcSecretReferenceRepository(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource"));
    }

    @Override
    public void register(VerifiedFacilityScope scope, SecretReference reference) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(reference, "reference");
        if (!scope.tenantId().equals(reference.tenantId())
                || !scope.facilityId().equals(reference.facilityId())
                || reference.state() != SecretReferenceState.ACTIVE
                || reference.rowVersion() != 0) {
            throw new IllegalArgumentException("A new secret reference must be active, unversioned and scope-matched");
        }
        int inserted;
        try {
            inserted = jdbcTemplate.update(
                    REGISTER,
                    reference.id().value(),
                    reference.provider().name(),
                    reference.backendBindingId(),
                    reference.purpose().name(),
                    scope.tenantId().value(),
                    scope.facilityId().value());
        } catch (DataIntegrityViolationException ignored) {
            throw new SecretReferenceUnavailableException();
        }
        if (inserted != 1) {
            throw new SecretReferenceUnavailableException();
        }
    }

    @Override
    public Optional<SecretReference> findUsable(VerifiedFacilityScope scope, SecretReferenceId referenceId) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(referenceId, "referenceId");
        return jdbcTemplate.query(
                FIND_USABLE,
                statement -> {
                    statement.setObject(1, scope.tenantId().value());
                    statement.setObject(2, scope.facilityId().value());
                    statement.setObject(3, referenceId.value());
                },
                resultSet -> resultSet.next() ? Optional.of(mapReference(resultSet)) : Optional.empty());
    }

    @Override
    public void bindToEndpoint(
            VerifiedFacilityScope scope, EndpointId endpointId, SecretReferenceId referenceId) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(endpointId, "endpointId");
        Objects.requireNonNull(referenceId, "referenceId");
        int inserted;
        try {
            inserted = jdbcTemplate.update(
                    BIND_TO_ENDPOINT,
                    scope.tenantId().value(),
                    scope.facilityId().value(),
                    endpointId.value(),
                    referenceId.value());
        } catch (DataIntegrityViolationException ignored) {
            throw new SecretReferenceUnavailableException();
        }
        if (inserted != 1) {
            throw new SecretReferenceUnavailableException();
        }
    }

    @Override
    public List<SecretReferenceExport> exportEndpointReferences(
            VerifiedFacilityScope scope, EndpointId endpointId) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(endpointId, "endpointId");
        return jdbcTemplate.query(
                EXPORT_ENDPOINT_REFERENCES,
                (resultSet, rowNumber) -> new SecretReferenceExport(
                        new SecretReferenceId(resultSet.getObject("secret_reference_id", java.util.UUID.class))
                                .externalForm(),
                        SecretProvider.valueOf(resultSet.getString("provider_kind")),
                        SecretPurpose.valueOf(resultSet.getString("purpose")),
                        SecretReferenceState.valueOf(resultSet.getString("reference_state")),
                        resultSet.getLong("row_version"),
                        true),
                scope.tenantId().value(),
                scope.facilityId().value(),
                endpointId.value());
    }

    private static SecretReference mapReference(ResultSet resultSet) throws SQLException {
        return new SecretReference(
                new SecretReferenceId(resultSet.getObject("secret_reference_id", java.util.UUID.class)),
                new TenantId(resultSet.getObject("tenant_id", java.util.UUID.class)),
                new FacilityId(resultSet.getObject("facility_id", java.util.UUID.class)),
                SecretProvider.valueOf(resultSet.getString("provider_kind")),
                resultSet.getObject("backend_binding_id", java.util.UUID.class),
                SecretPurpose.valueOf(resultSet.getString("purpose")),
                SecretReferenceState.valueOf(resultSet.getString("reference_state")),
                resultSet.getLong("row_version"));
    }
}
