package io.hyperhealth.connect.controlplane.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.lang.reflect.Modifier;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.hyperhealth.connect.controlplane.audit.InventoryActorType;
import io.hyperhealth.connect.controlplane.audit.InventoryAuditContext;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.ApplicationId;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.EndpointId;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.FacilityId;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.TenantId;
import io.hyperhealth.connect.controlplane.inventory.api.ScopedEndpointController;
import io.hyperhealth.connect.controlplane.secret.SecretReferenceRepository;

class ScopedInventoryServiceTest {

    @Test
    void keepsScopeAsTheFirstParameterOfEveryRepositoryOperation() {
        assertScopeFirst(ScopedEndpointRepository.class);
        assertScopeFirst(SecretReferenceRepository.class);
    }

    @Test
    void keepsScopeAsTheFirstParameterOfEveryPublicServiceAndApiOperation() {
        assertThat(Arrays.stream(ScopedInventoryService.class.getDeclaredMethods())
                        .filter(method -> Modifier.isPublic(method.getModifiers())))
                .isNotEmpty()
                .allSatisfy(method -> {
                    assertThat(method.getParameterTypes()).isNotEmpty();
                    assertThat(method.getParameterTypes()[0]).isEqualTo(VerifiedFacilityScope.class);
                });

        assertThat(Arrays.stream(ScopedEndpointController.class.getDeclaredMethods())
                        .filter(method -> Modifier.isPublic(method.getModifiers())))
                .isNotEmpty()
                .allSatisfy(method -> {
                    assertThat(method.getParameterTypes()).isNotEmpty();
                    assertThat(method.getParameterTypes()[0]).isEqualTo(VerifiedFacilityScope.class);
                });
    }

    @Test
    void rejectsAMissingScopeBeforeCallingPersistence() {
        ScopedEndpointRepository repository = mock(ScopedEndpointRepository.class);
        ScopedInventoryService service = new ScopedInventoryService(repository);

        assertThatThrownBy(() -> service.getEndpoint(
                        null,
                        new InventoryActor("synthetic-subject", "synthetic-client", InventoryActorType.HUMAN),
                        InventoryAuditContext.create(null),
                        new EndpointId(UUID.randomUUID())))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("scope");
        verifyNoInteractions(repository);
    }

    @Test
    void computesIdempotencyExpiryFromInjectedClockAcrossADstOverlap() {
        Instant overlapInstant = Instant.parse("2026-10-25T01:30:00Z");
        Duration retention = Duration.ofHours(24);
        ScopedEndpointRepository repository = mock(ScopedEndpointRepository.class);
        ScopedInventoryService service = new ScopedInventoryService(
                repository,
                Clock.fixed(overlapInstant, ZoneOffset.UTC),
                retention);
        VerifiedFacilityScope scope = new VerifiedFacilityScope(
                new TenantId(UUID.fromString("10000000-0000-4000-8000-000000000001")),
                new FacilityId(UUID.fromString("30000000-0000-4000-8000-000000000001")));
        InventoryActor actor = new InventoryActor(
                "synthetic-subject", "synthetic-client", InventoryActorType.HUMAN);
        InventoryAuditContext auditContext = InventoryAuditContext.create(null);
        ApplicationId applicationId = new ApplicationId(
                UUID.fromString("40000000-0000-4000-8000-000000000001"));
        String displayName = "HHC-SYNTHETIC DST endpoint";

        service.createEndpoint(
                scope,
                actor,
                auditContext,
                UUID.fromString("50000000-0000-4000-8000-000000000001"),
                applicationId,
                displayName);

        ArgumentCaptor<EndpointIdempotency> idempotency =
                ArgumentCaptor.forClass(EndpointIdempotency.class);
        verify(repository).createEndpoint(
                eq(scope),
                eq(actor),
                eq(auditContext),
                idempotency.capture(),
                eq(applicationId),
                eq(displayName));
        assertThat(idempotency.getValue().expiresAt()).isEqualTo(overlapInstant.plus(retention));
    }

    private static void assertScopeFirst(Class<?> repositoryType) {
        assertThat(Arrays.stream(repositoryType.getDeclaredMethods()))
                .isNotEmpty()
                .allSatisfy(method -> {
                    assertThat(method.getParameterTypes()).isNotEmpty();
                    assertThat(method.getParameterTypes()[0]).isEqualTo(VerifiedFacilityScope.class);
                });
    }
}
