package io.hyperhealth.connect.controlplane.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.hyperhealth.connect.controlplane.audit.InventoryActorType;
import io.hyperhealth.connect.controlplane.audit.InventoryAuditContext;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.EndpointId;
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

    private static void assertScopeFirst(Class<?> repositoryType) {
        assertThat(Arrays.stream(repositoryType.getDeclaredMethods()))
                .isNotEmpty()
                .allSatisfy(method -> {
                    assertThat(method.getParameterTypes()).isNotEmpty();
                    assertThat(method.getParameterTypes()[0]).isEqualTo(VerifiedFacilityScope.class);
                });
    }
}
