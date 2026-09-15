package io.hyperhealth.connect.controlplane.secret;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import io.hyperhealth.connect.controlplane.inventory.InventoryId.FacilityId;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.TenantId;

class SecretReferenceTest {

    @Test
    void usesCanonicalOpaqueIdentifiersAndRejectsNilValues() {
        SecretReferenceId id = SecretReferenceId.newId();

        assertThat(SecretReferenceId.parse(id.externalForm())).isEqualTo(id);
        assertThatThrownBy(() -> SecretReferenceId.parse(id.value().toString()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SecretReferenceId(new UUID(0, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exportAndDiagnosticRepresentationNeverExposeTheBackendBinding() throws Exception {
        UUID backendBindingId = UUID.randomUUID();
        SecretReference reference = SecretReference.active(
                SecretReferenceId.newId(),
                new TenantId(UUID.randomUUID()),
                new FacilityId(UUID.randomUUID()),
                SecretProvider.HASHICORP_VAULT,
                backendBindingId,
                SecretPurpose.OAUTH_CLIENT_CREDENTIAL);

        String exported = new ObjectMapper().writeValueAsString(reference.toExport());

        assertThat(exported)
                .contains(reference.id().externalForm())
                .contains("\"requiresRebinding\":true")
                .doesNotContain(backendBindingId.toString())
                .doesNotContain("backendBindingId")
                .doesNotContain("secretValue")
                .doesNotContain("password")
                .doesNotContain("token");
        assertThat(reference.toString())
                .contains("backendBindingId=[REDACTED]")
                .doesNotContain(backendBindingId.toString());
    }

    @Test
    void cannotCreateAnExportThatDoesNotRequireEnvironmentRebinding() {
        assertThatThrownBy(() -> new SecretReferenceExport(
                        SecretReferenceId.newId().externalForm(),
                        SecretProvider.EXTERNAL_BROKER,
                        SecretPurpose.ENDPOINT_API_TOKEN,
                        SecretReferenceState.ACTIVE,
                        0,
                        false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
