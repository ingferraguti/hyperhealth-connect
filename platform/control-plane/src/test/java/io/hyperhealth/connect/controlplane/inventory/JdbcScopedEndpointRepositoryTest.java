package io.hyperhealth.connect.controlplane.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import io.hyperhealth.connect.controlplane.inventory.InventoryId.EndpointId;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.FacilityId;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.TenantId;
import io.hyperhealth.connect.controlplane.inventory.api.InventoryProblemAdvice;
import io.hyperhealth.connect.controlplane.inventory.api.ScopedEndpointController;
import io.hyperhealth.connect.controlplane.inventory.api.VerifiedFacilityScopeArgumentResolver;

@Testcontainers
class JdbcScopedEndpointRepositoryTest {

    private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse(
                    "postgres:18.6-bookworm@sha256:1c59e2c3c818eaa0f0628f695b36e7c9e362d6b219b36a54a32df645cbd7e1af")
            .asCompatibleSubstituteFor("postgres");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(POSTGRES_IMAGE)
            .withDatabaseName("hhc_control")
            .withUsername("hhc_runtime")
            .withPassword("synthetic-test-password");

    private JdbcScopedEndpointRepository repository;

    @BeforeEach
    void prepareDatabase() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS platform_core CASCADE");
        }
        Flyway.configure()
                .configuration(Map.of("flyway.postgresql.transactional.lock", "false"))
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas("platform_core")
                .defaultSchema("platform_core")
                .createSchemas(true)
                .cleanDisabled(true)
                .load()
                .migrate();

        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        repository = new JdbcScopedEndpointRepository(dataSource);
    }

    @Test
    void returnsOnlyAnEndpointMatchingBothTenantAndFacilityPredicates() throws SQLException {
        InventoryFixture tenantAFirstFacility = createEndpointFixture("Tenant A / Facility 1");
        InventoryFixture tenantASecondFacility = createEndpointFixture(
                tenantAFirstFacility.tenantId(), "Tenant A / Facility 2");
        InventoryFixture tenantBFacility = createEndpointFixture("Tenant B / Facility 1");

        VerifiedFacilityScope authorized = tenantAFirstFacility.scope();
        ScopedEndpoint endpoint = repository
                .findEndpoint(authorized, tenantAFirstFacility.endpointId())
                .orElseThrow();

        assertThat(endpoint.tenantId()).isEqualTo(authorized.tenantId());
        assertThat(endpoint.facilityId()).isEqualTo(authorized.facilityId());
        assertThat(endpoint.endpointId()).isEqualTo(tenantAFirstFacility.endpointId());
        assertThat(endpoint.displayName()).isEqualTo("HHC-SYNTHETIC Tenant A / Facility 1 Endpoint");

        assertThat(repository.findEndpoint(
                        tenantASecondFacility.scope(), tenantAFirstFacility.endpointId()))
                .isEmpty();
        assertThat(repository.findEndpoint(tenantBFacility.scope(), tenantAFirstFacility.endpointId()))
                .isEmpty();
        assertThat(repository.findEndpoint(
                        new VerifiedFacilityScope(
                                tenantAFirstFacility.tenantId(), tenantBFacility.facilityId()),
                        tenantAFirstFacility.endpointId()))
                .isEmpty();
    }

    @Test
    void excludesDecommissionedEndpointsFromTheCurrentScopedReadModel() throws SQLException {
        InventoryFixture fixture = createEndpointFixture("Decommissioned");
        execute("""
                UPDATE platform_core.endpoint
                   SET lifecycle_state = 'DECOMMISSIONED',
                       decommissioned_at = transaction_timestamp(),
                       updated_at = transaction_timestamp(),
                       row_version = row_version + 1
                 WHERE endpoint_id = ?
                """, fixture.endpointId().value());

        assertThat(repository.findEndpoint(fixture.scope(), fixture.endpointId())).isEmpty();
    }

    @Test
    void deniesASiblingFacilityBeforeItsRepresentationCrossesTheApiBoundary() throws Exception {
        InventoryFixture authorized = createEndpointFixture("Authorized Facility");
        InventoryFixture sibling = createEndpointFixture(authorized.tenantId(), "Sibling Facility");
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                        new ScopedEndpointController(new ScopedInventoryService(repository)))
                .setCustomArgumentResolvers(new VerifiedFacilityScopeArgumentResolver())
                .setControllerAdvice(new InventoryProblemAdvice())
                .build();

        mockMvc.perform(get("/api/v1/endpoints/{endpointId}", sibling.endpointId().externalForm())
                        .requestAttr(
                                VerifiedFacilityScopeArgumentResolver.REQUEST_ATTRIBUTE,
                                authorized.scope()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HHC-INV-404-001"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("HHC-SYNTHETIC Sibling Facility Endpoint"))));
    }

    private static InventoryFixture createEndpointFixture(String label) throws SQLException {
        return createEndpointFixture(new TenantId(UUID.randomUUID()), label);
    }

    private static InventoryFixture createEndpointFixture(TenantId tenantId, String label) throws SQLException {
        UUID organizationId = UUID.randomUUID();
        FacilityId facilityId = new FacilityId(UUID.randomUUID());
        UUID applicationId = UUID.randomUUID();
        EndpointId endpointId = new EndpointId(UUID.randomUUID());

        if (queryCount("SELECT count(*) FROM platform_core.tenant WHERE tenant_id = ?", tenantId.value()) == 0) {
            allocate(tenantId.value(), "TENANT");
            execute(
                    "INSERT INTO platform_core.tenant (tenant_id, display_name) VALUES (?, ?)",
                    tenantId.value(),
                    "HHC-SYNTHETIC " + label + " Tenant");
        }
        allocate(organizationId, "ORGANIZATION");
        execute("""
                INSERT INTO platform_core.organization
                    (organization_id, tenant_id, display_name)
                VALUES (?, ?, ?)
                """, organizationId, tenantId.value(), "HHC-SYNTHETIC " + label + " Organization");
        allocate(facilityId.value(), "FACILITY");
        execute("""
                INSERT INTO platform_core.facility
                    (facility_id, tenant_id, organization_id, display_name)
                VALUES (?, ?, ?, ?)
                """, facilityId.value(), tenantId.value(), organizationId, "HHC-SYNTHETIC " + label + " Facility");
        allocate(applicationId, "APPLICATION");
        execute("""
                INSERT INTO platform_core.application
                    (application_id, tenant_id, organization_id, facility_id, display_name)
                VALUES (?, ?, ?, ?, ?)
                """, applicationId, tenantId.value(), organizationId, facilityId.value(), "HHC-SYNTHETIC " + label + " Application");
        allocate(endpointId.value(), "ENDPOINT");
        execute("""
                INSERT INTO platform_core.endpoint
                    (endpoint_id, tenant_id, organization_id, facility_id, application_id, display_name)
                VALUES (?, ?, ?, ?, ?, ?)
                """, endpointId.value(), tenantId.value(), organizationId, facilityId.value(), applicationId, "HHC-SYNTHETIC " + label + " Endpoint");

        return new InventoryFixture(tenantId, facilityId, endpointId);
    }

    private static void allocate(UUID id, String type) throws SQLException {
        execute(
                "INSERT INTO platform_core.resource_identity (resource_id, resource_type) VALUES (?, ?)",
                id,
                type);
    }

    private static void execute(String sql, Object... values) throws SQLException {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            statement.executeUpdate();
        }
    }

    private static long queryCount(String sql, Object value) throws SQLException {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, value);
            try (java.sql.ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : 0;
            }
        }
    }

    private static Connection connection() throws SQLException {
        return java.sql.DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private record InventoryFixture(TenantId tenantId, FacilityId facilityId, EndpointId endpointId) {
        VerifiedFacilityScope scope() {
            return new VerifiedFacilityScope(tenantId, facilityId);
        }
    }
}
