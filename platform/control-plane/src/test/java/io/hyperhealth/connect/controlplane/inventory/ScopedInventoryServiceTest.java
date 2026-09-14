package io.hyperhealth.connect.controlplane.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.hyperhealth.connect.controlplane.inventory.InventoryId.EndpointId;

class ScopedInventoryServiceTest {

    @Test
    void keepsScopeAsTheFirstParameterOfEveryRepositoryOperation() {
        assertThat(Arrays.stream(ScopedEndpointRepository.class.getDeclaredMethods()))
                .allSatisfy(method -> {
                    assertThat(method.getParameterTypes()).isNotEmpty();
                    assertThat(method.getParameterTypes()[0]).isEqualTo(VerifiedFacilityScope.class);
                });
    }

    @Test
    void rejectsAMissingScopeBeforeCallingPersistence() {
        ScopedEndpointRepository repository = mock(ScopedEndpointRepository.class);
        ScopedInventoryService service = new ScopedInventoryService(repository);

        assertThatThrownBy(() -> service.getEndpoint(null, new EndpointId(UUID.randomUUID())))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("scope");
        verifyNoInteractions(repository);
    }
}
