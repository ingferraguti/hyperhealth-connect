package io.hyperhealth.connect.controlplane.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class PlatformCoreMigrationTest {

    private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse(
                    "postgres:18.6-bookworm@sha256:1c59e2c3c818eaa0f0628f695b36e7c9e362d6b219b36a54a32df645cbd7e1af")
            .asCompatibleSubstituteFor("postgres");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(POSTGRES_IMAGE)
            .withDatabaseName("hhc_control")
            .withUsername("hhc_migration")
            .withPassword("synthetic-test-password");

    @BeforeEach
    void resetSchema() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS platform_core CASCADE");
        }
    }

    @Test
    void migratesAndValidatesAnEmptyPostgresql18Database() throws SQLException {
        Flyway flyway = flyway(null);

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(4);
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();

        assertThat(queryStrings("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'platform_core'
                ORDER BY table_name
                """))
                .contains(
                        "application",
                        "endpoint",
                        "facility",
                        "flyway_schema_history",
                        "lifecycle_state",
                        "organization",
                        "resource_identity",
                        "runtime_cell",
                        "runtime_cell_scope_assignment",
                        "secret_reference",
                        "endpoint_secret_binding",
                        "tenant");
        assertThat(queryLong("""
                SELECT count(*)
                FROM pg_constraint c
                JOIN pg_namespace n ON n.oid = c.connamespace
                WHERE n.nspname = 'platform_core' AND NOT c.convalidated
                """))
                .isZero();
        assertThat(queryLong("""
                SELECT count(*)
                FROM pg_index i
                JOIN pg_class t ON t.oid = i.indrelid
                JOIN pg_namespace n ON n.oid = t.relnamespace
                WHERE n.nspname = 'platform_core' AND NOT i.indisvalid
                """))
                .isZero();
        assertThat(queryStrings("""
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = 'platform_core'
                ORDER BY indexname
                """))
                .contains(
                        "ix_resource_identity_type_allocated",
                        "ix_tenant_active_updated",
                        "ix_organization_scope_active",
                        "ix_facility_scope_active",
                        "ix_application_scope_active",
                        "ix_endpoint_scope_active",
                        "ix_runtime_cell_active_updated",
                        "ix_runtime_cell_scope_by_cell_history",
                        "ix_runtime_cell_scope_lookup_active",
                        "ix_secret_reference_scope_state",
                        "ix_endpoint_secret_binding_reference_active",
                        "uq_endpoint_secret_binding_active",
                        "uq_runtime_cell_scope_assignment_active");
    }

    @Test
    void upgradesAnNMinusOneSchemaWithoutLosingExistingInventory() throws SQLException {
        assertThat(flyway("1").migrate().migrationsExecuted).isEqualTo(1);
        UUID tenantId = UUID.randomUUID();
        allocate(tenantId, "TENANT");
        execute("INSERT INTO platform_core.tenant (tenant_id, display_name) VALUES (?, ?)", tenantId, "Synthetic Tenant");

        assertThat(flyway(null).migrate().migrationsExecuted).isEqualTo(3);
        assertThat(queryLong("SELECT count(*) FROM platform_core.tenant WHERE tenant_id = '" + tenantId + "'::uuid"))
                .isEqualTo(1L);
        assertThat(flyway(null).validateWithResult().validationSuccessful).isTrue();
    }

    @Test
    void constraintsRejectCrossTenantAncestryWrongTypesAndIdentityMutation() throws SQLException {
        flyway(null).migrate();
        UUID tenantA = createTenant("Synthetic Tenant A");
        UUID tenantB = createTenant("Synthetic Tenant B");
        UUID organizationA = createOrganization(tenantA, "Synthetic Organization A");
        UUID facilityA = createFacility(tenantA, organizationA, "Synthetic Facility A");
        UUID applicationA = createApplication(tenantA, organizationA, facilityA, "Synthetic Application A");
        UUID endpointA = createEndpoint(
                tenantA, organizationA, facilityA, applicationA, "Synthetic Endpoint A");

        assertThat(queryLong("SELECT count(*) FROM platform_core.endpoint WHERE endpoint_id = '"
                        + endpointA
                        + "'::uuid"))
                .isEqualTo(1L);

        UUID invalidFacility = UUID.randomUUID();
        allocate(invalidFacility, "FACILITY");
        assertSqlState("23503", () -> execute(
                "INSERT INTO platform_core.facility (facility_id, tenant_id, organization_id, display_name) VALUES (?, ?, ?, ?)",
                invalidFacility,
                tenantB,
                organizationA,
                "Cross-tenant Facility"));

        assertSqlState("23503", () -> execute(
                "INSERT INTO platform_core.organization (organization_id, tenant_id, display_name) VALUES (?, ?, ?)",
                tenantA,
                tenantA,
                "Wrongly typed identity"));

        assertSqlState("23000", () -> execute(
                "UPDATE platform_core.tenant SET tenant_id = ? WHERE tenant_id = ?",
                UUID.randomUUID(),
                tenantA));
        assertSqlState("23000", () -> execute(
                "DELETE FROM platform_core.tenant WHERE tenant_id = ?",
                tenantA));
    }

    @Test
    void runtimeCellRequiresAnImmutableExplicitScopeAndRejectsDuplicateAssignment() throws SQLException {
        flyway(null).migrate();
        UUID tenantId = createTenant("Synthetic Runtime Tenant");
        UUID validCellId = UUID.randomUUID();

        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            allocate(connection, validCellId, "RUNTIME_CELL");
            execute(connection,
                    "INSERT INTO platform_core.runtime_cell (runtime_cell_id, display_name) VALUES (?, ?)",
                    validCellId,
                    "Synthetic Runtime Cell");
            execute(connection, """
                    INSERT INTO platform_core.runtime_cell_scope_assignment
                        (runtime_cell_id, scope_level, tenant_id)
                    VALUES (?, 'TENANT', ?)
                    """, validCellId, tenantId);
            connection.commit();
        }

        assertSqlState("23505", () -> execute("""
                INSERT INTO platform_core.runtime_cell_scope_assignment
                    (runtime_cell_id, scope_level, tenant_id)
                VALUES (?, 'TENANT', ?)
                """, validCellId, tenantId));

        assertSqlState("23514", () -> execute("""
                INSERT INTO platform_core.runtime_cell_scope_assignment
                    (runtime_cell_id, scope_level, tenant_id, facility_id)
                VALUES (?, 'TENANT', ?, ?)
                """, validCellId, tenantId, UUID.randomUUID()));

        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            execute(connection, """
                    UPDATE platform_core.runtime_cell_scope_assignment
                    SET revoked_at = transaction_timestamp()
                    WHERE runtime_cell_id = ? AND revoked_at IS NULL
                    """, validCellId);
            assertThatThrownBy(connection::commit)
                    .isInstanceOf(SQLException.class)
                    .extracting(exception -> ((SQLException) exception).getSQLState())
                    .isEqualTo("23514");
            connection.rollback();
        }

        UUID unassignedCellId = UUID.randomUUID();
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            allocate(connection, unassignedCellId, "RUNTIME_CELL");
            execute(connection,
                    "INSERT INTO platform_core.runtime_cell (runtime_cell_id, display_name) VALUES (?, ?)",
                    unassignedCellId,
                    "Unassigned Cell");
            assertThatThrownBy(connection::commit)
                    .isInstanceOf(SQLException.class)
                    .extracting(exception -> ((SQLException) exception).getSQLState())
                    .isEqualTo("23514");
            connection.rollback();
        }
    }

    @Test
    void secretReferenceSchemaAcceptsOnlyOpaqueTypedMetadata() throws SQLException {
        flyway(null).migrate();
        UUID tenantId = createTenant("Synthetic Secret Tenant");
        UUID organizationId = createOrganization(tenantId, "Synthetic Secret Organization");
        UUID facilityId = createFacility(tenantId, organizationId, "Synthetic Secret Facility");
        UUID referenceId = UUID.randomUUID();
        UUID backendBindingId = UUID.randomUUID();

        execute("""
                INSERT INTO platform_core.secret_reference
                    (secret_reference_id, tenant_id, organization_id, facility_id,
                     provider_kind, backend_binding_id, purpose)
                VALUES (?, ?, ?, ?, 'HASHICORP_VAULT', ?, 'ENDPOINT_API_TOKEN')
                """, referenceId, tenantId, organizationId, facilityId, backendBindingId);

        assertThat(queryStrings("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'platform_core'
                  AND table_name = 'secret_reference'
                ORDER BY ordinal_position
                """))
                .containsExactly(
                        "secret_reference_id",
                        "tenant_id",
                        "organization_id",
                        "facility_id",
                        "provider_kind",
                        "backend_binding_id",
                        "purpose",
                        "reference_state",
                        "row_version",
                        "created_at",
                        "updated_at",
                        "revoked_at")
                .doesNotContain(
                        "secret_value",
                        "value",
                        "payload",
                        "ciphertext",
                        "password",
                        "token",
                        "private_key",
                        "certificate",
                        "provider_path");

        assertSqlState("23514", () -> execute("""
                INSERT INTO platform_core.secret_reference
                    (secret_reference_id, tenant_id, organization_id, facility_id,
                     provider_kind, backend_binding_id, purpose)
                VALUES (?, ?, ?, ?, ?, ?, 'ENDPOINT_API_TOKEN')
                """,
                UUID.randomUUID(),
                tenantId,
                organizationId,
                facilityId,
                "SYNTHETIC_SECRET_VALUE_CANNOT_BE_STORED",
                UUID.randomUUID()));
        assertSqlState("23000", () -> execute(
                "UPDATE platform_core.secret_reference SET backend_binding_id = ? WHERE secret_reference_id = ?",
                UUID.randomUUID(),
                referenceId));
        assertSqlState("23000", () -> execute(
                "DELETE FROM platform_core.secret_reference WHERE secret_reference_id = ?", referenceId));

        execute("""
                UPDATE platform_core.secret_reference
                   SET reference_state = 'REVOKED',
                       revoked_at = transaction_timestamp(),
                       updated_at = transaction_timestamp(),
                       row_version = row_version + 1
                 WHERE secret_reference_id = ?
                """, referenceId);
        assertSqlState("23514", () -> execute("""
                UPDATE platform_core.secret_reference
                   SET reference_state = 'ACTIVE',
                       revoked_at = NULL,
                       updated_at = transaction_timestamp(),
                       row_version = row_version + 1
                 WHERE secret_reference_id = ?
                """, referenceId));
    }

    private static Flyway flyway(String target) {
        FluentConfiguration configuration = Flyway.configure()
                .configuration(Map.of("flyway.postgresql.transactional.lock", "false"))
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas("platform_core")
                .defaultSchema("platform_core")
                .createSchemas(true)
                .cleanDisabled(true)
                .validateMigrationNaming(true)
                .validateOnMigrate(true)
                .outOfOrder(false);
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static UUID createTenant(String displayName) throws SQLException {
        UUID tenantId = UUID.randomUUID();
        allocate(tenantId, "TENANT");
        execute("INSERT INTO platform_core.tenant (tenant_id, display_name) VALUES (?, ?)", tenantId, displayName);
        return tenantId;
    }

    private static UUID createOrganization(UUID tenantId, String displayName) throws SQLException {
        UUID organizationId = UUID.randomUUID();
        allocate(organizationId, "ORGANIZATION");
        execute(
                "INSERT INTO platform_core.organization (organization_id, tenant_id, display_name) VALUES (?, ?, ?)",
                organizationId,
                tenantId,
                displayName);
        return organizationId;
    }

    private static UUID createFacility(UUID tenantId, UUID organizationId, String displayName) throws SQLException {
        UUID facilityId = UUID.randomUUID();
        allocate(facilityId, "FACILITY");
        execute("""
                INSERT INTO platform_core.facility
                    (facility_id, tenant_id, organization_id, display_name)
                VALUES (?, ?, ?, ?)
                """, facilityId, tenantId, organizationId, displayName);
        return facilityId;
    }

    private static UUID createApplication(
            UUID tenantId, UUID organizationId, UUID facilityId, String displayName) throws SQLException {
        UUID applicationId = UUID.randomUUID();
        allocate(applicationId, "APPLICATION");
        execute("""
                INSERT INTO platform_core.application
                    (application_id, tenant_id, organization_id, facility_id, display_name)
                VALUES (?, ?, ?, ?, ?)
                """, applicationId, tenantId, organizationId, facilityId, displayName);
        return applicationId;
    }

    private static UUID createEndpoint(
            UUID tenantId,
            UUID organizationId,
            UUID facilityId,
            UUID applicationId,
            String displayName) throws SQLException {
        UUID endpointId = UUID.randomUUID();
        allocate(endpointId, "ENDPOINT");
        execute("""
                INSERT INTO platform_core.endpoint
                    (endpoint_id, tenant_id, organization_id, facility_id, application_id, display_name)
                VALUES (?, ?, ?, ?, ?, ?)
                """, endpointId, tenantId, organizationId, facilityId, applicationId, displayName);
        return endpointId;
    }

    private static void allocate(UUID resourceId, String resourceType) throws SQLException {
        try (Connection connection = connection()) {
            allocate(connection, resourceId, resourceType);
        }
    }

    private static void allocate(Connection connection, UUID resourceId, String resourceType) throws SQLException {
        execute(connection,
                "INSERT INTO platform_core.resource_identity (resource_id, resource_type) VALUES (?, ?)",
                resourceId,
                resourceType);
    }

    private static void execute(String sql, Object... values) throws SQLException {
        try (Connection connection = connection()) {
            execute(connection, sql, values);
        }
    }

    private static void execute(Connection connection, String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            statement.executeUpdate();
        }
    }

    private static long queryLong(String sql) throws SQLException {
        try (Connection connection = connection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }

    private static List<String> queryStrings(String sql) throws SQLException {
        try (Connection connection = connection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            java.util.ArrayList<String> values = new java.util.ArrayList<>();
            while (result.next()) {
                values.add(result.getString(1));
            }
            return List.copyOf(values);
        }
    }

    private static void assertSqlState(String expected, SqlAction action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(SQLException.class)
                .extracting(exception -> ((SQLException) exception).getSQLState())
                .isEqualTo(expected);
    }

    @FunctionalInterface
    private interface SqlAction {
        void run() throws SQLException;
    }
}
