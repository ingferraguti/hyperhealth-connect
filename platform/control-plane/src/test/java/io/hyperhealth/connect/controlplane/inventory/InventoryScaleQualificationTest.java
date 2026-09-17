package io.hyperhealth.connect.controlplane.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import io.hyperhealth.connect.controlplane.audit.InventoryActorType;
import io.hyperhealth.connect.controlplane.audit.InventoryAuditContext;
import io.hyperhealth.connect.controlplane.audit.JdbcInventoryAuditJournal;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.EndpointId;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.FacilityId;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.TenantId;

@Testcontainers
class InventoryScaleQualificationTest {

    private static final String SEED_RESOURCE = "seed/p1-0110-inventory-scale.sql";
    private static final int TOTAL_ENDPOINTS = 12_000;
    private static final int HOT_FACILITY_ENDPOINTS = 6_000;
    private static final UUID TENANT_ALPHA = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID FACILITY_ALPHA_ONE = UUID.fromString("30000000-0000-4000-8000-000000000001");
    private static final UUID TENANT_BETA = UUID.fromString("10000000-0000-4000-8000-000000000002");
    private static final UUID FACILITY_BETA_TWO = UUID.fromString("30000000-0000-4000-8000-000000000004");
    private static final UUID APPLICATION_BETA_TWO = UUID.fromString("40000000-0000-4000-8000-000000000008");
    private static final UUID FIRST_POSSIBLE_ENDPOINT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID HOT_FACILITY_MIDPOINT = UUID.fromString("50000000-0000-4000-8000-000000003000");
    private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse(
                    "postgres:18.6-bookworm@sha256:1c59e2c3c818eaa0f0628f695b36e7c9e362d6b219b36a54a32df645cbd7e1af")
            .asCompatibleSubstituteFor("postgres");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(POSTGRES_IMAGE)
            .withDatabaseName("hhc_control")
            .withUsername("hhc_scale_test")
            .withPassword("synthetic-test-password");

    private static JdbcScopedEndpointRepository repository;
    private static JdbcInventoryAuditJournal auditJournal;

    @BeforeAll
    static void prepareScaleCorpus() throws SQLException {
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
        try (Connection connection = connection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(SEED_RESOURCE));
            try (Statement statement = connection.createStatement()) {
                statement.execute("VACUUM (ANALYZE) platform_core.endpoint");
            }
        }

        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        auditJournal = auditJournal(dataSource);
        repository = new JdbcScopedEndpointRepository(dataSource, auditJournal);
    }

    @Test
    void loadsDeterministicSyntheticEnterpriseTopologyAndHotScope() throws SQLException {
        assertThat(queryLong("SELECT count(*) FROM platform_core.tenant")).isEqualTo(2);
        assertThat(queryLong("SELECT count(*) FROM platform_core.organization")).isEqualTo(2);
        assertThat(queryLong("SELECT count(*) FROM platform_core.facility")).isEqualTo(4);
        assertThat(queryLong("SELECT count(*) FROM platform_core.application")).isEqualTo(8);
        assertThat(queryLong("SELECT count(*) FROM platform_core.endpoint")).isEqualTo(TOTAL_ENDPOINTS);
        assertThat(queryLong("SELECT count(*) FROM platform_core.runtime_cell")).isEqualTo(2);
        assertThat(queryLong("SELECT count(*) FROM platform_core.runtime_cell_scope_assignment"))
                .isEqualTo(4);
        assertThat(queryLong("""
                SELECT count(*) FROM platform_core.resource_identity
                 WHERE resource_type = 'ENDPOINT'
                """))
                .isEqualTo(TOTAL_ENDPOINTS);
        assertThat(queryLong("""
                SELECT count(*) FROM platform_core.endpoint
                 WHERE tenant_id = '10000000-0000-4000-8000-000000000001'
                   AND facility_id = '30000000-0000-4000-8000-000000000001'
                """))
                .isEqualTo(HOT_FACILITY_ENDPOINTS);
        assertThat(queryLong("""
                SELECT count(*)
                  FROM (
                    SELECT display_name FROM platform_core.tenant
                    UNION ALL SELECT display_name FROM platform_core.organization
                    UNION ALL SELECT display_name FROM platform_core.facility
                    UNION ALL SELECT display_name FROM platform_core.application
                    UNION ALL SELECT display_name FROM platform_core.endpoint
                    UNION ALL SELECT display_name FROM platform_core.runtime_cell
                  ) AS inventory_names
                 WHERE display_name NOT LIKE 'HHC-SYNTHETIC%'
                """))
                .isZero();
        assertThat(queryLong("""
                SELECT count(*)
                  FROM platform_core.endpoint endpoint
                  JOIN platform_core.facility facility
                    ON facility.tenant_id = endpoint.tenant_id
                   AND facility.facility_id = endpoint.facility_id
                  JOIN platform_core.application application
                    ON application.tenant_id = endpoint.tenant_id
                   AND application.facility_id = endpoint.facility_id
                   AND application.application_id = endpoint.application_id
                """))
                .isEqualTo(TOTAL_ENDPOINTS);
        assertThat(queryLong("""
                SELECT count(*)
                  FROM platform_core.endpoint
                 WHERE lifecycle_state = 'ACTIVE'
                """))
                .isEqualTo(10_800);
        assertThat(queryLong("""
                SELECT count(*)
                  FROM platform_core.endpoint
                 WHERE lifecycle_state = 'SUSPENDED'
                """))
                .isEqualTo(1_200);
    }

    @Test
    void paginatesTheHotFacilityExactlyOnceWithBoundedKeysetPagesAndScopedAudit() throws SQLException {
        VerifiedFacilityScope scope = new VerifiedFacilityScope(
                new TenantId(TENANT_ALPHA), new FacilityId(FACILITY_ALPHA_ONE));
        InventoryActor actor = new InventoryActor(
                "hhc-synthetic-scale-operator", "hhc-scale-harness", InventoryActorType.WORKLOAD);
        Set<EndpointId> seen = new LinkedHashSet<>();
        Optional<EndpointId> cursor = Optional.empty();
        int pages = 0;

        do {
            EndpointPageSlice page = repository.listEndpoints(
                    scope,
                    actor,
                    InventoryAuditContext.create(null),
                    new EndpointQuery(Optional.empty(), Optional.empty(), cursor, EndpointQuery.MAXIMUM_LIMIT));
            pages++;
            assertThat(page.items()).hasSizeLessThanOrEqualTo(EndpointQuery.MAXIMUM_LIMIT);
            assertThat(page.items()).allSatisfy(endpoint -> {
                assertThat(endpoint.tenantId().value()).isEqualTo(TENANT_ALPHA);
                assertThat(endpoint.facilityId().value()).isEqualTo(FACILITY_ALPHA_ONE);
                assertThat(seen.add(endpoint.endpointId())).isTrue();
            });
            cursor = page.hasMore()
                    ? Optional.of(page.items().get(page.items().size() - 1).endpointId())
                    : Optional.empty();
        } while (cursor.isPresent());

        assertThat(seen).hasSize(HOT_FACILITY_ENDPOINTS);
        assertThat(pages).isEqualTo(60);
        assertThat(queryLong("""
                SELECT count(*) FROM platform_core.inventory_audit_event
                 WHERE tenant_id = '10000000-0000-4000-8000-000000000001'
                   AND facility_id = '30000000-0000-4000-8000-000000000001'
                   AND action_code = 'ENDPOINT_LIST'
                """))
                .isEqualTo(pages);
        assertThat(auditJournal.verify(scope).valid()).isTrue();
    }

    @Test
    void qualifiesScopedCoveringPlansAndPublishesNonProductionLatencyEvidence()
            throws SQLException, IOException {
        String facilityPlan = explain("""
                SELECT endpoint_id, tenant_id, organization_id, facility_id, application_id,
                       display_name, lifecycle_state, row_version
                  FROM platform_core.endpoint
                 WHERE tenant_id = ? AND facility_id = ?
                   AND lifecycle_state <> 'DECOMMISSIONED'
                   AND endpoint_id > ?
                 ORDER BY endpoint_id
                 LIMIT 101
                """, TENANT_BETA, FACILITY_BETA_TWO, FIRST_POSSIBLE_ENDPOINT);
        String applicationPlan = explain("""
                SELECT endpoint_id, tenant_id, organization_id, facility_id, application_id,
                       display_name, lifecycle_state, row_version
                  FROM platform_core.endpoint
                 WHERE tenant_id = ? AND facility_id = ? AND application_id = ?
                   AND lifecycle_state <> 'DECOMMISSIONED'
                   AND endpoint_id > ?
                 ORDER BY endpoint_id
                 LIMIT 101
                """, TENANT_BETA, FACILITY_BETA_TWO, APPLICATION_BETA_TWO, FIRST_POSSIBLE_ENDPOINT);

        assertThat(facilityPlan)
                .contains("ix_endpoint_facility_page_active")
                .doesNotContain("Seq Scan on endpoint");
        assertThat(applicationPlan)
                .contains("ix_endpoint_facility_application_page_active")
                .doesNotContain("Seq Scan on endpoint");

        List<Long> samplesNanos = measureWarmFacilityPages(20, 100);
        List<Long> ordered = samplesNanos.stream().sorted().toList();
        double p95Ms = ordered.get((int) Math.ceil(ordered.size() * 0.95) - 1) / 1_000_000.0;
        double maximumMs = ordered.get(ordered.size() - 1) / 1_000_000.0;
        assertThat(p95Ms).isLessThanOrEqualTo(500.0);
        assertThat(maximumMs).isLessThan(5_000.0);

        writeEvidence(p95Ms, maximumMs, ordered.size());
    }

    private static List<Long> measureWarmFacilityPages(int warmupIterations, int measuredIterations)
            throws SQLException {
        String sql = """
                SELECT endpoint_id, tenant_id, organization_id, facility_id, application_id,
                       display_name, lifecycle_state, row_version
                  FROM platform_core.endpoint
                 WHERE tenant_id = ? AND facility_id = ?
                   AND lifecycle_state <> 'DECOMMISSIONED'
                   AND endpoint_id > ?
                 ORDER BY endpoint_id
                 LIMIT 101
                """;
        List<Long> samples = new ArrayList<>(measuredIterations);
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(5);
            for (int iteration = 0; iteration < warmupIterations + measuredIterations; iteration++) {
                statement.setObject(1, TENANT_ALPHA);
                statement.setObject(2, FACILITY_ALPHA_ONE);
                statement.setObject(3, HOT_FACILITY_MIDPOINT);
                long started = System.nanoTime();
                int rows = 0;
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        rows++;
                    }
                }
                long elapsed = System.nanoTime() - started;
                assertThat(rows).isEqualTo(101);
                if (iteration >= warmupIterations) {
                    samples.add(elapsed);
                }
            }
        }
        return List.copyOf(samples);
    }

    private static String explain(String sql, Object... values) throws SQLException {
        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement(
                        "EXPLAIN (ANALYZE, BUFFERS, SETTINGS, FORMAT TEXT) " + sql)) {
            statement.setQueryTimeout(5);
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            StringBuilder plan = new StringBuilder();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    plan.append(resultSet.getString(1)).append('\n');
                }
            }
            return plan.toString();
        }
    }

    private static void writeEvidence(double p95Ms, double maximumMs, int samples) throws IOException, SQLException {
        Path directory = Path.of("target", "phase1-p1-0110-evidence");
        Files.createDirectories(directory);
        String json = String.format(Locale.ROOT, """
                {
                  "schemaVersion": 1,
                  "activity": "P1-0110",
                  "generatedAt": "%s",
                  "classification": "synthetic-only",
                  "environment": "developer-testcontainers-non-qualifying",
                  "postgresVersion": "%s",
                  "seedSha256": "%s",
                  "tenants": 2,
                  "facilities": 4,
                  "endpoints": %d,
                  "hotFacilityEndpoints": %d,
                  "measuredSamples": %d,
                  "warmQueryP95Ms": %.3f,
                  "warmQueryMaximumMs": %.3f,
                  "facilityPlanIndex": "ix_endpoint_facility_page_active",
                  "applicationPlanIndex": "ix_endpoint_facility_application_page_active",
                  "productionCapacityClaim": false,
                  "outcome": "PASS"
                }
                """,
                Instant.now(),
                queryString("SHOW server_version"),
                seedDigest(),
                TOTAL_ENDPOINTS,
                HOT_FACILITY_ENDPOINTS,
                samples,
                p95Ms,
                maximumMs);
        Files.writeString(directory.resolve("inventory-scale-test-report.json"), json, StandardCharsets.UTF_8);
    }

    private static String seedDigest() throws IOException {
        ClassPathResource resource = new ClassPathResource(SEED_RESOURCE);
        try (var input = resource.getInputStream()) {
            return HexFormat.of().formatHex(sha256().digest(input.readAllBytes()));
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", exception);
        }
    }

    private static JdbcInventoryAuditJournal auditJournal(DataSource dataSource) {
        return new JdbcInventoryAuditJournal(
                dataSource,
                Clock.systemUTC(),
                repeatedByte(0x11),
                repeatedByte(0x22),
                "test-integrity-key-v1",
                repeatedByte(0x33),
                repeatedByte(0x44));
    }

    private static byte[] repeatedByte(int value) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, (byte) value);
        return bytes;
    }

    private static long queryLong(String sql) throws SQLException {
        try (Connection connection = connection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getLong(1);
        }
    }

    private static String queryString(String sql) throws SQLException {
        try (Connection connection = connection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString(1);
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
