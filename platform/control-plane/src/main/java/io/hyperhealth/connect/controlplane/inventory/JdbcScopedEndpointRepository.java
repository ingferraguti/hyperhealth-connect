package io.hyperhealth.connect.controlplane.inventory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;

import io.hyperhealth.connect.controlplane.inventory.InventoryId.ApplicationId;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.EndpointId;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.FacilityId;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.OrganizationId;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.TenantId;

/** PostgreSQL repository that places scope predicates in the same query as the resource lookup. */
public final class JdbcScopedEndpointRepository implements ScopedEndpointRepository {

    private static final String FIND_ENDPOINT = """
            SELECT endpoint_id,
                   tenant_id,
                   organization_id,
                   facility_id,
                   application_id,
                   display_name,
                   lifecycle_state,
                   row_version
              FROM platform_core.endpoint
             WHERE tenant_id = ?
               AND facility_id = ?
               AND endpoint_id = ?
               AND lifecycle_state <> 'DECOMMISSIONED'
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcScopedEndpointRepository(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource"));
    }

    @Override
    public Optional<ScopedEndpoint> findEndpoint(VerifiedFacilityScope scope, EndpointId endpointId) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(endpointId, "endpointId");

        return jdbcTemplate.query(
                FIND_ENDPOINT,
                statement -> {
                    statement.setObject(1, scope.tenantId().value());
                    statement.setObject(2, scope.facilityId().value());
                    statement.setObject(3, endpointId.value());
                },
                resultSet -> resultSet.next() ? Optional.of(mapEndpoint(resultSet)) : Optional.empty());
    }

    private static ScopedEndpoint mapEndpoint(ResultSet resultSet) throws SQLException {
        return new ScopedEndpoint(
                new EndpointId(resultSet.getObject("endpoint_id", java.util.UUID.class)),
                new TenantId(resultSet.getObject("tenant_id", java.util.UUID.class)),
                new OrganizationId(resultSet.getObject("organization_id", java.util.UUID.class)),
                new FacilityId(resultSet.getObject("facility_id", java.util.UUID.class)),
                new ApplicationId(resultSet.getObject("application_id", java.util.UUID.class)),
                resultSet.getString("display_name"),
                InventoryLifecycleState.valueOf(resultSet.getString("lifecycle_state")),
                resultSet.getLong("row_version"));
    }
}
