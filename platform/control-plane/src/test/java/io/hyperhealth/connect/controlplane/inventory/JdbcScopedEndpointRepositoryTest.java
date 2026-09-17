package io.hyperhealth.connect.controlplane.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import io.hyperhealth.connect.controlplane.inventory.InventoryId.EndpointId;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.ApplicationId;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.FacilityId;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.TenantId;
import io.hyperhealth.connect.controlplane.inventory.api.InventoryProblemAdvice;
import io.hyperhealth.connect.controlplane.inventory.api.InventoryCursorCodec;
import io.hyperhealth.connect.controlplane.inventory.api.ScopedEndpointController;
import io.hyperhealth.connect.controlplane.inventory.api.VerifiedFacilityScopeArgumentResolver;
import io.hyperhealth.connect.controlplane.audit.InventoryActorType;
import io.hyperhealth.connect.controlplane.audit.InventoryAuditContext;
import io.hyperhealth.connect.controlplane.audit.InventoryAuditIntent;
import io.hyperhealth.connect.controlplane.audit.InventoryAuditJournal;
import io.hyperhealth.connect.controlplane.audit.JdbcInventoryAuditJournal;
import io.hyperhealth.connect.controlplane.secret.JdbcSecretReferenceRepository;
import io.hyperhealth.connect.controlplane.secret.SecretProvider;
import io.hyperhealth.connect.controlplane.secret.SecretPurpose;
import io.hyperhealth.connect.controlplane.secret.SecretReference;
import io.hyperhealth.connect.controlplane.secret.SecretReferenceId;
import io.hyperhealth.connect.controlplane.secret.SecretReferenceUnavailableException;
import io.hyperhealth.connect.controlplane.security.AuthenticatedIdentity;
import io.hyperhealth.connect.controlplane.security.ControlPlaneRole;
import io.hyperhealth.connect.controlplane.security.HhcJwtAuthenticationToken;
import io.hyperhealth.connect.controlplane.security.PrincipalType;

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
    private JdbcSecretReferenceRepository secretRepository;
    private JdbcInventoryAuditJournal auditJournal;
    private DataSource dataSource;

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

        PGSimpleDataSource configuredDataSource = new PGSimpleDataSource();
        configuredDataSource.setURL(POSTGRES.getJdbcUrl());
        configuredDataSource.setUser(POSTGRES.getUsername());
        configuredDataSource.setPassword(POSTGRES.getPassword());
        dataSource = configuredDataSource;
        auditJournal = new JdbcInventoryAuditJournal(
                dataSource,
                Clock.systemUTC(),
                repeatedByte(0x11),
                repeatedByte(0x22),
                "test-integrity-key-v1",
                repeatedByte(0x33),
                repeatedByte(0x44));
        repository = new JdbcScopedEndpointRepository(dataSource, auditJournal);
        secretRepository = new JdbcSecretReferenceRepository(dataSource);
    }

    @Test
    void returnsOnlyAnEndpointMatchingBothTenantAndFacilityPredicates() throws SQLException {
        InventoryFixture tenantAFirstFacility = createEndpointFixture("Tenant A / Facility 1");
        InventoryFixture tenantASecondFacility = createEndpointFixture(
                tenantAFirstFacility.tenantId(), "Tenant A / Facility 2");
        InventoryFixture tenantBFacility = createEndpointFixture("Tenant B / Facility 1");

        VerifiedFacilityScope authorized = tenantAFirstFacility.scope();
        ScopedEndpoint endpoint = repository
                .findEndpoint(authorized, actor(), context(), tenantAFirstFacility.endpointId())
                .orElseThrow();

        assertThat(endpoint.tenantId()).isEqualTo(authorized.tenantId());
        assertThat(endpoint.facilityId()).isEqualTo(authorized.facilityId());
        assertThat(endpoint.endpointId()).isEqualTo(tenantAFirstFacility.endpointId());
        assertThat(endpoint.displayName()).isEqualTo("HHC-SYNTHETIC Tenant A / Facility 1 Endpoint");

        assertThat(repository.findEndpoint(
                        tenantASecondFacility.scope(), actor(), context(), tenantAFirstFacility.endpointId()))
                .isEmpty();
        assertThat(repository.findEndpoint(
                        tenantBFacility.scope(), actor(), context(), tenantAFirstFacility.endpointId()))
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

        assertThat(repository.findEndpoint(fixture.scope(), actor(), context(), fixture.endpointId())).isEmpty();
    }

    @Test
    void createsOnceAndReplaysTheExactResponseForTheSameIdempotencyKey() throws SQLException {
        InventoryFixture fixture = createEndpointFixture("Idempotent creation");
        ScopedInventoryService service = new ScopedInventoryService(repository);
        InventoryActor actor = actor();
        UUID key = UUID.randomUUID();

        EndpointCreationResult first = service.createEndpoint(
                fixture.scope(), actor, context(), key, fixture.applicationId(), "HHC-SYNTHETIC new endpoint");
        EndpointCreationResult replay = service.createEndpoint(
                fixture.scope(), actor, context(), key, fixture.applicationId(), "HHC-SYNTHETIC new endpoint");

        assertThat(first.replayed()).isFalse();
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.endpoint()).isEqualTo(first.endpoint());
        assertThat(queryCount(
                        "SELECT count(*) FROM platform_core.inventory_idempotency_record WHERE tenant_id = ?",
                        fixture.tenantId().value()))
                .isEqualTo(1);
        assertThat(queryCount(
                        "SELECT count(*) FROM platform_core.endpoint WHERE tenant_id = ?",
                        fixture.tenantId().value()))
                .isEqualTo(2);
        assertThat(auditJournal.verify(fixture.scope()))
                .satisfies(verification -> {
                    assertThat(verification.valid()).isTrue();
                    assertThat(verification.eventCount()).isEqualTo(2);
                });

        assertThatThrownBy(() -> service.createEndpoint(
                        fixture.scope(), actor, context(), key, fixture.applicationId(), "A different request"))
                .isInstanceOf(InventoryIdempotencyConflictException.class);
    }

    @Test
    void concurrentRetriesCommitExactlyOneEndpoint() throws Exception {
        InventoryFixture fixture = createEndpointFixture("Concurrent idempotency");
        ScopedInventoryService service = new ScopedInventoryService(repository);
        InventoryActor actor = actor();
        UUID key = UUID.randomUUID();
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<EndpointCreationResult> request = () -> {
                start.await();
                return service.createEndpoint(
                        fixture.scope(), actor, context(), key, fixture.applicationId(),
                        "HHC-SYNTHETIC concurrent endpoint");
            };
            Future<EndpointCreationResult> firstFuture = executor.submit(request);
            Future<EndpointCreationResult> secondFuture = executor.submit(request);
            start.countDown();
            EndpointCreationResult first = firstFuture.get();
            EndpointCreationResult second = secondFuture.get();

            assertThat(first.endpoint()).isEqualTo(second.endpoint());
            assertThat(List.of(first.replayed(), second.replayed())).containsExactlyInAnyOrder(false, true);
        }
        assertThat(queryCount(
                        "SELECT count(*) FROM platform_core.inventory_idempotency_record WHERE tenant_id = ?",
                        fixture.tenantId().value()))
                .isEqualTo(1);
        assertThat(queryCount(
                        "SELECT count(*) FROM platform_core.endpoint WHERE tenant_id = ?",
                        fixture.tenantId().value()))
                .isEqualTo(2);
        assertThat(auditJournal.verify(fixture.scope()))
                .satisfies(verification -> {
                    assertThat(verification.valid()).isTrue();
                    assertThat(verification.eventCount()).isEqualTo(2);
                });
    }

    @Test
    void keysetPaginationAndFiltersRemainInsideTheVerifiedFacility() throws SQLException {
        InventoryFixture fixture = createEndpointFixture("Pagination");
        InventoryFixture sibling = createEndpointFixture(fixture.tenantId(), "Pagination sibling");
        ScopedInventoryService service = new ScopedInventoryService(repository);
        InventoryActor actor = actor();
        for (int index = 0; index < 4; index++) {
            service.createEndpoint(
                    fixture.scope(),
                    actor,
                    context(),
                    UUID.randomUUID(),
                    fixture.applicationId(),
                    "HHC-SYNTHETIC page endpoint " + index);
        }

        EndpointPageSlice complete = repository.listEndpoints(
                fixture.scope(),
                actor,
                context(),
                new EndpointQuery(Optional.of(fixture.applicationId()), Optional.empty(), Optional.empty(), 100));
        EndpointPageSlice first = repository.listEndpoints(
                fixture.scope(),
                actor,
                context(),
                new EndpointQuery(Optional.of(fixture.applicationId()), Optional.empty(), Optional.empty(), 2));
        EndpointPageSlice second = repository.listEndpoints(
                fixture.scope(),
                actor,
                context(),
                new EndpointQuery(
                        Optional.of(fixture.applicationId()),
                        Optional.empty(),
                        Optional.of(first.items().getLast().endpointId()),
                        100));

        assertThat(complete.items()).hasSize(5);
        assertThat(first.items()).hasSize(2);
        assertThat(first.hasMore()).isTrue();
        assertThat(second.items()).hasSize(3);
        assertThat(first.items()).doesNotContainAnyElementsOf(second.items());
        assertThat(complete.items()).containsExactlyElementsOf(
                java.util.stream.Stream.concat(first.items().stream(), second.items().stream()).toList());
        assertThat(complete.items()).noneMatch(endpoint -> endpoint.facilityId().equals(sibling.facilityId()));
    }

    @Test
    void optimisticUpdateRejectsStaleWritesAndMakesDecommissionTerminal() throws SQLException {
        InventoryFixture fixture = createEndpointFixture("Optimistic update");
        ScopedInventoryService service = new ScopedInventoryService(repository);
        InventoryActor actor = actor();

        ScopedEndpoint renamed = service.updateEndpoint(
                fixture.scope(),
                actor,
                context(),
                fixture.endpointId(),
                0,
                new EndpointPatch(Optional.of("HHC-SYNTHETIC renamed"), Optional.empty()));
        assertThat(renamed.rowVersion()).isEqualTo(1);
        assertThat(renamed.displayName()).isEqualTo("HHC-SYNTHETIC renamed");

        assertThatThrownBy(() -> service.updateEndpoint(
                        fixture.scope(),
                        actor,
                        context(),
                        fixture.endpointId(),
                        0,
                        new EndpointPatch(Optional.of("stale"), Optional.empty())))
                .isInstanceOf(InventoryPreconditionFailedException.class);

        ScopedEndpoint decommissioned = service.updateEndpoint(
                fixture.scope(),
                actor,
                context(),
                fixture.endpointId(),
                1,
                new EndpointPatch(Optional.empty(), Optional.of(InventoryLifecycleState.DECOMMISSIONED)));
        assertThat(decommissioned.rowVersion()).isEqualTo(2);
        assertThat(decommissioned.lifecycleState()).isEqualTo(InventoryLifecycleState.DECOMMISSIONED);
        assertThat(repository.findEndpoint(fixture.scope(), actor, context(), fixture.endpointId())).isEmpty();
        assertThat(queryCount(
                        "SELECT count(*) FROM platform_core.resource_identity WHERE resource_id = ? AND decommissioned_at IS NOT NULL",
                        fixture.endpointId().value()))
                .isEqualTo(1);
        assertThatThrownBy(() -> service.updateEndpoint(
                        fixture.scope(),
                        actor,
                        context(),
                        fixture.endpointId(),
                        2,
                        new EndpointPatch(Optional.empty(), Optional.of(InventoryLifecycleState.ACTIVE))))
                .isInstanceOf(InventoryResourceNotFoundException.class);
    }

    @Test
    void deniesASiblingFacilityBeforeItsRepresentationCrossesTheApiBoundary() throws Exception {
        InventoryFixture authorized = createEndpointFixture("Authorized Facility");
        InventoryFixture sibling = createEndpointFixture(authorized.tenantId(), "Sibling Facility");
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                        new ScopedEndpointController(
                                new ScopedInventoryService(repository),
                                new InventoryCursorCodec(new byte[32], Duration.ofMinutes(15))))
                .setCustomArgumentResolvers(new VerifiedFacilityScopeArgumentResolver())
                .setControllerAdvice(new InventoryProblemAdvice())
                .build();

        mockMvc.perform(get("/api/v1/endpoints/{endpointId}", sibling.endpointId().externalForm())
                        .principal(authentication(authorized.scope()))
                        .requestAttr(
                                VerifiedFacilityScopeArgumentResolver.REQUEST_ATTRIBUTE,
                                authorized.scope()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HHC-INV-404-001"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("HHC-SYNTHETIC Sibling Facility Endpoint"))));
    }

    @Test
    void deniesTheCompleteCrossFacilityAndCrossTenantMatrixWithoutSideEffectsOrEnumeration() throws Exception {
        InventoryFixture authorized = createEndpointFixture("Matrix Authorized");
        InventoryFixture sibling = createEndpointFixture(authorized.tenantId(), "Matrix Sibling");
        InventoryFixture otherTenant = createEndpointFixture("Matrix Other Tenant");
        ScopedInventoryService service = new ScopedInventoryService(repository);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                        new ScopedEndpointController(
                                service,
                                new InventoryCursorCodec(new byte[32], Duration.ofMinutes(15))))
                .setCustomArgumentResolvers(new VerifiedFacilityScopeArgumentResolver())
                .setControllerAdvice(new InventoryProblemAdvice())
                .build();

        long endpointCountBefore = queryCount("SELECT count(*) FROM platform_core.endpoint");
        long identityCountBefore = queryCount(
                "SELECT count(*) FROM platform_core.resource_identity WHERE resource_type = 'ENDPOINT'");

        for (InventoryFixture foreign : List.of(sibling, otherTenant)) {
            // Repository and service reads are scoped before a row can be materialized.
            assertThat(repository.findEndpoint(
                            authorized.scope(), actor(), context(), foreign.endpointId()))
                    .isEmpty();
            assertThatThrownBy(() -> service.getEndpoint(
                            authorized.scope(), actor(), context(), foreign.endpointId()))
                    .isInstanceOf(InventoryResourceNotFoundException.class)
                    .hasMessageNotContaining(foreign.endpointId().externalForm());

            // A foreign filter is a valid, empty query and cannot turn into an existence oracle.
            assertThat(repository.listEndpoints(
                            authorized.scope(),
                            actor(),
                            context(),
                            new EndpointQuery(
                                    Optional.of(foreign.applicationId()),
                                    Optional.empty(),
                                    Optional.empty(),
                                    10)))
                    .satisfies(page -> {
                        assertThat(page.items()).isEmpty();
                        assertThat(page.hasMore()).isFalse();
                    });

            // A failed cross-scope create rolls back its idempotency claim and identity allocation.
            assertThatThrownBy(() -> service.createEndpoint(
                            authorized.scope(),
                            actor(),
                            context(),
                            UUID.randomUUID(),
                            foreign.applicationId(),
                            "HHC-SYNTHETIC forbidden create"))
                    .isInstanceOf(InventoryResourceNotFoundException.class)
                    .hasMessageNotContaining(foreign.applicationId().externalForm());

            // A failed cross-scope patch cannot modify the foreign row or reveal its version.
            assertThatThrownBy(() -> service.updateEndpoint(
                            authorized.scope(),
                            actor(),
                            context(),
                            foreign.endpointId(),
                            0,
                            new EndpointPatch(Optional.of("HHC-SYNTHETIC forbidden rename"), Optional.empty())))
                    .isInstanceOf(InventoryResourceNotFoundException.class)
                    .hasMessageNotContaining(foreign.endpointId().externalForm());

            mockMvc.perform(get("/api/v1/endpoints/{endpointId}", foreign.endpointId().externalForm())
                            .principal(authentication(authorized.scope()))
                            .requestAttr(
                                    VerifiedFacilityScopeArgumentResolver.REQUEST_ATTRIBUTE,
                                    authorized.scope())
                            .header("X-Tenant-ID", foreign.tenantId().externalForm())
                            .header("X-Facility-ID", foreign.facilityId().externalForm()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("HHC-INV-404-001"))
                    .andExpect(jsonPath("$.detail").value(
                            "The resource does not exist or is not visible in the current scope."))
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString(foreign.endpointId().externalForm()))))
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString("HHC-SYNTHETIC Matrix"))));

            mockMvc.perform(get("/api/v1/endpoints")
                            .principal(authentication(authorized.scope()))
                            .requestAttr(
                                    VerifiedFacilityScopeArgumentResolver.REQUEST_ATTRIBUTE,
                                    authorized.scope())
                            .queryParam("applicationId", foreign.applicationId().externalForm()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isEmpty())
                    .andExpect(jsonPath("$.page.hasMore").value(false))
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString(foreign.endpointId().externalForm()))));

            mockMvc.perform(post("/api/v1/endpoints")
                            .principal(authentication(authorized.scope()))
                            .requestAttr(
                                    VerifiedFacilityScopeArgumentResolver.REQUEST_ATTRIBUTE,
                                    authorized.scope())
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"applicationId":"%s","displayName":"HHC-SYNTHETIC forbidden create"}
                                    """.formatted(foreign.applicationId().externalForm())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("HHC-INV-404-001"))
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString(foreign.applicationId().externalForm()))));

            mockMvc.perform(patch("/api/v1/endpoints/{endpointId}", foreign.endpointId().externalForm())
                            .principal(authentication(authorized.scope()))
                            .requestAttr(
                                    VerifiedFacilityScopeArgumentResolver.REQUEST_ATTRIBUTE,
                                    authorized.scope())
                            .header("If-Match", "\"rv-0\"")
                            .contentType("application/merge-patch+json")
                            .content("{\"displayName\":\"HHC-SYNTHETIC forbidden rename\"}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("HHC-INV-404-001"))
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString(foreign.endpointId().externalForm()))));

            assertThat(queryCount(
                            "SELECT count(*) FROM platform_core.endpoint "
                                    + "WHERE endpoint_id = ? AND display_name LIKE 'HHC-SYNTHETIC Matrix % Endpoint' "
                                    + "AND row_version = 0",
                            foreign.endpointId().value()))
                    .isEqualTo(1);
            assertThat(queryCount(
                            "SELECT count(*) FROM platform_core.inventory_audit_event "
                                    + "WHERE tenant_id = ? AND facility_id = ?",
                            foreign.tenantId().value(),
                            foreign.facilityId().value()))
                    .isZero();
        }

        assertThat(queryCount("SELECT count(*) FROM platform_core.endpoint")).isEqualTo(endpointCountBefore);
        assertThat(queryCount(
                        "SELECT count(*) FROM platform_core.resource_identity WHERE resource_type = 'ENDPOINT'"))
                .isEqualTo(identityCountBefore);
        assertThat(queryCount(
                        "SELECT count(*) FROM platform_core.inventory_idempotency_record "
                                + "WHERE tenant_id = ? AND facility_id = ?",
                        authorized.tenantId().value(),
                        authorized.facilityId().value()))
                .isZero();
        assertThat(queryCount(
                        "SELECT count(*) FROM platform_core.inventory_audit_event "
                                + "WHERE tenant_id = ? AND facility_id = ?",
                        authorized.tenantId().value(),
                        authorized.facilityId().value()))
                .isEqualTo(10);
        assertThat(queryCount(
                        "SELECT count(*) FROM platform_core.inventory_audit_event "
                                + "WHERE tenant_id = ? AND facility_id = ? "
                                + "AND action_code = 'ENDPOINT_READ' AND outcome_code = 'FAILURE' "
                                + "AND reason_code = 'NOT_FOUND_OR_NOT_VISIBLE'",
                        authorized.tenantId().value(),
                        authorized.facilityId().value()))
                .isEqualTo(6);
        assertThat(auditJournal.verify(authorized.scope()).valid()).isTrue();
    }

    @Test
    void recordsAnImmutablePseudonymousAndVerifiableInventoryAuditChain() throws Exception {
        InventoryFixture fixture = createEndpointFixture("Audit chain");
        ScopedInventoryService service = new ScopedInventoryService(repository);
        InventoryActor actor = new InventoryActor(
                "synthetic-audit-subject", "hhc-control-plane-ui", InventoryActorType.HUMAN);

        EndpointCreationResult created = service.createEndpoint(
                fixture.scope(), actor, context(), UUID.randomUUID(), fixture.applicationId(),
                "HHC-SYNTHETIC audited endpoint");
        service.getEndpoint(fixture.scope(), actor, context(), created.endpoint().endpointId());
        service.listEndpoints(
                fixture.scope(), actor, context(),
                new EndpointQuery(Optional.empty(), Optional.empty(), Optional.empty(), 10));
        ScopedEndpoint renamed = service.updateEndpoint(
                fixture.scope(), actor, context(), created.endpoint().endpointId(), 0,
                new EndpointPatch(Optional.of("HHC-SYNTHETIC audited endpoint renamed"), Optional.empty()));
        service.updateEndpoint(
                fixture.scope(), actor, context(), created.endpoint().endpointId(), renamed.rowVersion(),
                new EndpointPatch(Optional.empty(), Optional.of(InventoryLifecycleState.DECOMMISSIONED)));

        assertThat(queryStrings("""
                SELECT action_code
                  FROM platform_core.inventory_audit_event
                 WHERE tenant_id = '%s'::uuid AND facility_id = '%s'::uuid
                 ORDER BY scope_sequence
                """.formatted(fixture.tenantId().value(), fixture.facilityId().value())))
                .containsExactly(
                        "ENDPOINT_CREATE", "ENDPOINT_READ", "ENDPOINT_LIST",
                        "ENDPOINT_UPDATE", "ENDPOINT_DECOMMISSION");
        assertThat(auditJournal.verify(fixture.scope()).valid()).isTrue();
        assertThat(queryCount(
                        "SELECT count(*) FROM platform_core.inventory_audit_event "
                                + "WHERE actor_id_hash = convert_to(?, 'UTF8') OR resource_id_hash = convert_to(?, 'UTF8')",
                        actor.subject(),
                        created.endpoint().endpointId().externalForm()))
                .isZero();
        assertSqlState("23000", () -> execute(
                "UPDATE platform_core.inventory_audit_event SET reason_code = 'TAMPERED' WHERE tenant_id = ?",
                fixture.tenantId().value()));
        assertSqlState("23000", () -> execute(
                "DELETE FROM platform_core.inventory_audit_event WHERE tenant_id = ?",
                fixture.tenantId().value()));
        assertSqlState("23000", () -> execute("TRUNCATE platform_core.inventory_audit_event"));

        execute("ALTER TABLE platform_core.inventory_audit_event DISABLE TRIGGER USER");
        try {
            execute("""
                    UPDATE platform_core.inventory_audit_event
                       SET record_hash = decode(repeat('00', 32), 'hex')
                     WHERE tenant_id = ? AND facility_id = ? AND scope_sequence = 1
                    """, fixture.tenantId().value(), fixture.facilityId().value());
        } finally {
            execute("ALTER TABLE platform_core.inventory_audit_event ENABLE TRIGGER USER");
        }
        assertThat(auditJournal.verify(fixture.scope()))
                .satisfies(verification -> {
                    assertThat(verification.valid()).isFalse();
                    assertThat(verification.firstInvalidSequence()).isEqualTo(1);
                });
    }

    @Test
    void rollsBackTheBusinessMutationWhenTheAuditAppendFails() throws Exception {
        InventoryFixture fixture = createEndpointFixture("Audit rollback");
        InventoryAuditJournal failingJournal = Mockito.mock(InventoryAuditJournal.class);
        Mockito.doThrow(new IllegalStateException("synthetic audit sink failure"))
                .when(failingJournal)
                .append(Mockito.any(InventoryAuditIntent.class));
        JdbcScopedEndpointRepository failingRepository =
                new JdbcScopedEndpointRepository(dataSource, failingJournal);
        ScopedInventoryService service = new ScopedInventoryService(failingRepository);

        assertThatThrownBy(() -> service.createEndpoint(
                        fixture.scope(), actor(), context(), UUID.randomUUID(), fixture.applicationId(),
                        "HHC-SYNTHETIC must roll back"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("synthetic audit sink failure");
        assertThat(queryCount(
                        "SELECT count(*) FROM platform_core.endpoint WHERE tenant_id = ?",
                        fixture.tenantId().value()))
                .isEqualTo(1);
        assertThat(queryCount(
                        "SELECT count(*) FROM platform_core.inventory_idempotency_record WHERE tenant_id = ?",
                        fixture.tenantId().value()))
                .isZero();
    }

    @Test
    void persistsOnlyOpaqueSecretBindingsAndExportsOnlyPortableMetadata() throws Exception {
        InventoryFixture authorized = createEndpointFixture("Secret Authorized");
        InventoryFixture sibling = createEndpointFixture(authorized.tenantId(), "Secret Sibling");
        InventoryFixture otherTenant = createEndpointFixture("Secret Other Tenant");
        UUID backendBindingId = UUID.randomUUID();
        SecretReference reference = SecretReference.active(
                SecretReferenceId.newId(),
                authorized.tenantId(),
                authorized.facilityId(),
                SecretProvider.AZURE_KEY_VAULT,
                backendBindingId,
                SecretPurpose.OAUTH_CLIENT_CREDENTIAL);

        secretRepository.register(authorized.scope(), reference);
        secretRepository.bindToEndpoint(authorized.scope(), authorized.endpointId(), reference.id());

        SecretReference conflictingReference = SecretReference.active(
                SecretReferenceId.newId(),
                authorized.tenantId(),
                authorized.facilityId(),
                SecretProvider.AZURE_KEY_VAULT,
                backendBindingId,
                SecretPurpose.OAUTH_CLIENT_CREDENTIAL);
        assertThatThrownBy(() -> secretRepository.register(authorized.scope(), conflictingReference))
                .isInstanceOf(SecretReferenceUnavailableException.class)
                .hasMessageNotContaining(backendBindingId.toString())
                .hasMessageNotContaining(conflictingReference.id().externalForm())
                .hasNoCause();

        assertThat(secretRepository.findUsable(authorized.scope(), reference.id())).isPresent();
        assertThat(secretRepository.findUsable(sibling.scope(), reference.id())).isEmpty();
        assertThat(secretRepository.findUsable(otherTenant.scope(), reference.id())).isEmpty();
        assertThat(secretRepository.exportEndpointReferences(authorized.scope(), authorized.endpointId()))
                .singleElement()
                .satisfies(export -> {
                    assertThat(export.secretReferenceId()).isEqualTo(reference.id().externalForm());
                    assertThat(export.provider()).isEqualTo(SecretProvider.AZURE_KEY_VAULT);
                    assertThat(export.purpose()).isEqualTo(SecretPurpose.OAUTH_CLIENT_CREDENTIAL);
                    assertThat(export.requiresRebinding()).isTrue();
                    assertThat(export.toString()).doesNotContain(backendBindingId.toString());
                });

        assertThat(secretRepository.exportEndpointReferences(sibling.scope(), authorized.endpointId()))
                .isEmpty();
        assertThat(secretRepository.exportEndpointReferences(otherTenant.scope(), authorized.endpointId()))
                .isEmpty();
        assertThatThrownBy(() ->
                        secretRepository.bindToEndpoint(authorized.scope(), sibling.endpointId(), reference.id()))
                .isInstanceOf(SecretReferenceUnavailableException.class)
                .hasMessageNotContaining(reference.id().externalForm())
                .hasMessageNotContaining(sibling.endpointId().externalForm());
        assertThatThrownBy(() ->
                        secretRepository.bindToEndpoint(sibling.scope(), authorized.endpointId(), reference.id()))
                .isInstanceOf(SecretReferenceUnavailableException.class)
                .hasMessageNotContaining(reference.id().externalForm())
                .hasMessageNotContaining(authorized.endpointId().externalForm());
        assertThatThrownBy(() ->
                        secretRepository.bindToEndpoint(otherTenant.scope(), authorized.endpointId(), reference.id()))
                .isInstanceOf(SecretReferenceUnavailableException.class)
                .hasMessageNotContaining(reference.id().externalForm())
                .hasMessageNotContaining(authorized.endpointId().externalForm());
        assertThatThrownBy(() -> secretRepository.register(sibling.scope(), reference))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining(reference.id().externalForm())
                .hasMessageNotContaining(backendBindingId.toString());
        assertThat(queryCount(
                        "SELECT count(*) FROM platform_core.endpoint_secret_binding "
                                + "WHERE secret_reference_id = ?",
                        reference.id().value()))
                .isEqualTo(1);
    }

    @Test
    void permitsRotationOverlapAndExcludesRevokedReferencesFromRuntimeExports() throws Exception {
        InventoryFixture fixture = createEndpointFixture("Secret Rotation");
        SecretReference oldReference = SecretReference.active(
                SecretReferenceId.newId(),
                fixture.tenantId(),
                fixture.facilityId(),
                SecretProvider.HASHICORP_VAULT,
                UUID.randomUUID(),
                SecretPurpose.TLS_CLIENT_CERTIFICATE);
        SecretReference replacementReference = SecretReference.active(
                SecretReferenceId.newId(),
                fixture.tenantId(),
                fixture.facilityId(),
                SecretProvider.HASHICORP_VAULT,
                UUID.randomUUID(),
                SecretPurpose.TLS_CLIENT_CERTIFICATE);

        secretRepository.register(fixture.scope(), oldReference);
        secretRepository.register(fixture.scope(), replacementReference);
        secretRepository.bindToEndpoint(fixture.scope(), fixture.endpointId(), oldReference.id());
        secretRepository.bindToEndpoint(fixture.scope(), fixture.endpointId(), replacementReference.id());

        assertThat(secretRepository.exportEndpointReferences(fixture.scope(), fixture.endpointId()))
                .extracting(export -> export.secretReferenceId())
                .containsExactlyInAnyOrder(oldReference.id().externalForm(), replacementReference.id().externalForm());

        execute("""
                UPDATE platform_core.secret_reference
                   SET reference_state = 'REVOKED',
                       revoked_at = transaction_timestamp(),
                       updated_at = transaction_timestamp(),
                       row_version = row_version + 1
                 WHERE tenant_id = ?
                   AND facility_id = ?
                   AND secret_reference_id = ?
                """, fixture.tenantId().value(), fixture.facilityId().value(), oldReference.id().value());

        assertThat(secretRepository.findUsable(fixture.scope(), oldReference.id())).isEmpty();
        assertThat(secretRepository.exportEndpointReferences(fixture.scope(), fixture.endpointId()))
                .singleElement()
                .extracting(export -> export.secretReferenceId())
                .isEqualTo(replacementReference.id().externalForm());
        execute("""
                UPDATE platform_core.endpoint_secret_binding
                   SET retired_at = transaction_timestamp()
                 WHERE tenant_id = ?
                   AND facility_id = ?
                   AND endpoint_id = ?
                   AND secret_reference_id = ?
                   AND retired_at IS NULL
                """,
                fixture.tenantId().value(),
                fixture.facilityId().value(),
                fixture.endpointId().value(),
                oldReference.id().value());
        assertThatThrownBy(() -> execute("""
                        UPDATE platform_core.secret_reference
                           SET reference_state = 'ACTIVE',
                               revoked_at = NULL,
                               updated_at = transaction_timestamp(),
                               row_version = row_version + 1
                         WHERE secret_reference_id = ?
                        """, oldReference.id().value()))
                .isInstanceOf(SQLException.class)
                .extracting(exception -> ((SQLException) exception).getSQLState())
                .isEqualTo("23514");
        assertThatThrownBy(() -> execute("""
                        UPDATE platform_core.endpoint_secret_binding
                           SET retired_at = NULL
                         WHERE secret_reference_id = ?
                        """, oldReference.id().value()))
                .isInstanceOf(SQLException.class)
                .extracting(exception -> ((SQLException) exception).getSQLState())
                .isEqualTo("23514");
    }

    @Test
    void excludesSecretReferencesAfterTheBoundEndpointIsDecommissioned() throws Exception {
        InventoryFixture fixture = createEndpointFixture("Secret Decommissioned");
        SecretReference reference = SecretReference.active(
                SecretReferenceId.newId(),
                fixture.tenantId(),
                fixture.facilityId(),
                SecretProvider.AWS_SECRETS_MANAGER,
                UUID.randomUUID(),
                SecretPurpose.ENDPOINT_API_TOKEN);
        secretRepository.register(fixture.scope(), reference);
        secretRepository.bindToEndpoint(fixture.scope(), fixture.endpointId(), reference.id());

        execute("""
                UPDATE platform_core.endpoint
                   SET lifecycle_state = 'DECOMMISSIONED',
                       decommissioned_at = transaction_timestamp(),
                       updated_at = transaction_timestamp(),
                       row_version = row_version + 1
                 WHERE tenant_id = ?
                   AND facility_id = ?
                   AND endpoint_id = ?
                """, fixture.tenantId().value(), fixture.facilityId().value(), fixture.endpointId().value());

        assertThat(secretRepository.exportEndpointReferences(fixture.scope(), fixture.endpointId()))
                .isEmpty();
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

        return new InventoryFixture(
                tenantId, facilityId, new ApplicationId(applicationId), endpointId);
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

    private static long queryCount(String sql, Object... values) throws SQLException {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            try (java.sql.ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : 0;
            }
        }
    }

    private static List<String> queryStrings(String sql) throws SQLException {
        try (Connection connection = connection();
                Statement statement = connection.createStatement();
                java.sql.ResultSet resultSet = statement.executeQuery(sql)) {
            java.util.ArrayList<String> values = new java.util.ArrayList<>();
            while (resultSet.next()) {
                values.add(resultSet.getString(1));
            }
            return List.copyOf(values);
        }
    }

    private static InventoryActor actor() {
        return new InventoryActor("synthetic-operator", "hhc-control-plane-ui", InventoryActorType.HUMAN);
    }

    private static InventoryAuditContext context() {
        return InventoryAuditContext.create(null);
    }

    private static byte[] repeatedByte(int value) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, (byte) value);
        return bytes;
    }

    private static HhcJwtAuthenticationToken authentication(VerifiedFacilityScope scope) {
        HhcJwtAuthenticationToken authentication = Mockito.mock(HhcJwtAuthenticationToken.class);
        Mockito.when(authentication.isAuthenticated()).thenReturn(true);
        Mockito.when(authentication.identity()).thenReturn(new AuthenticatedIdentity(
                "synthetic-subject",
                "hhc-control-plane-ui",
                PrincipalType.HUMAN,
                java.util.Set.of(ControlPlaneRole.FACILITY_OPERATOR),
                scope));
        return authentication;
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

    private static Connection connection() throws SQLException {
        return java.sql.DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private record InventoryFixture(
            TenantId tenantId,
            FacilityId facilityId,
            ApplicationId applicationId,
            EndpointId endpointId) {
        VerifiedFacilityScope scope() {
            return new VerifiedFacilityScope(tenantId, facilityId);
        }
    }
}
