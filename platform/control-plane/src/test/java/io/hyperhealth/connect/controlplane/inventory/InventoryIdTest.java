package io.hyperhealth.connect.controlplane.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.hyperhealth.connect.controlplane.inventory.InventoryId.ApplicationId;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.EndpointId;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.FacilityId;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.OrganizationId;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.RuntimeCellId;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.TenantId;

class InventoryIdTest {

    @Test
    void generatedIdentifierRoundTripsThroughCanonicalExternalForm() {
        TenantId generated = TenantId.newId();

        assertThat(TenantId.parse(generated.toString())).isEqualTo(generated);
        assertThat(generated.toString()).startsWith("t-");
    }

    @Test
    void everyTypeUsesTheCanonicalEnvelopePrefix() {
        assertThat(TenantId.newId().prefix()).isEqualTo("t-");
        assertThat(OrganizationId.newId().prefix()).isEqualTo("o-");
        assertThat(FacilityId.newId().prefix()).isEqualTo("f-");
        assertThat(ApplicationId.newId().prefix()).isEqualTo("a-");
        assertThat(EndpointId.newId().prefix()).isEqualTo("ep-");
        assertThat(RuntimeCellId.newId().prefix()).isEqualTo("rc-");
    }

    @Test
    void identifierTypeIsPartOfTheExternalIdentity() {
        UUID value = UUID.fromString("173f26d6-6bf5-4c32-aefe-a7016753b6ae");

        assertThat(new TenantId(value).toString())
                .isNotEqualTo(new EndpointId(value).toString());
        assertThatThrownBy(() -> EndpointId.parse("t-" + value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ep-");
    }

    @Test
    void nilMalformedAndMissingIdentifiersAreRejected() {
        assertThatThrownBy(() -> new TenantId(new UUID(0L, 0L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nil UUID");
        assertThatThrownBy(() -> TenantId.parse("t-not-a-uuid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("canonical UUID");
        assertThatThrownBy(() -> TenantId.parse("t-1-1-1-1-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("canonical UUID");
        assertThatThrownBy(() -> new TenantId(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("value");
    }
}
