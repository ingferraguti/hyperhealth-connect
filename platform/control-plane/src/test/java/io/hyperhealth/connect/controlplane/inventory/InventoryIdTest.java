package io.hyperhealth.connect.controlplane.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

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

    @Test
    @ResourceLock("jvm-default-locale")
    void canonicalIdentifiersRoundTripExactlyUnderEuropeanAndTurkishLocales() {
        UUID value = UUID.fromString("00112233-4455-4677-8899-aabbccddeeff");
        String tenant = "t-00112233-4455-4677-8899-aabbccddeeff";
        String organization = "o-00112233-4455-4677-8899-aabbccddeeff";
        String facility = "f-00112233-4455-4677-8899-aabbccddeeff";
        String application = "a-00112233-4455-4677-8899-aabbccddeeff";
        String endpoint = "ep-00112233-4455-4677-8899-aabbccddeeff";
        String runtimeCell = "rc-00112233-4455-4677-8899-aabbccddeeff";
        Locale previous = Locale.getDefault();
        try {
            for (Locale locale : List.of(Locale.ITALIAN, Locale.ENGLISH, Locale.FRENCH, Locale.forLanguageTag("tr-TR"))) {
                Locale.setDefault(locale);
                assertThat(TenantId.parse(tenant)).isEqualTo(new TenantId(value));
                assertThat(TenantId.parse(tenant).externalForm()).isEqualTo(tenant);
                assertThat(OrganizationId.parse(organization).externalForm()).isEqualTo(organization);
                assertThat(FacilityId.parse(facility).externalForm()).isEqualTo(facility);
                assertThat(ApplicationId.parse(application).externalForm()).isEqualTo(application);
                assertThat(EndpointId.parse(endpoint).externalForm()).isEqualTo(endpoint);
                assertThat(RuntimeCellId.parse(runtimeCell).externalForm()).isEqualTo(runtimeCell);
            }
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void rejectsWhitespaceCaseVariantsAndUnicodeConfusablesInsteadOfRewritingIdentifiers() {
        String canonical = "ep-00112233-4455-4677-8899-aabbccddeeff";

        assertThatThrownBy(() -> EndpointId.parse(" " + canonical))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EndpointId.parse(canonical + " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EndpointId.parse(canonical.toUpperCase(Locale.ROOT)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EndpointId.parse("еp-00112233-4455-4677-8899-aabbccddeeff"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EndpointId.parse("ep-００１１２２３３-4455-4677-8899-aabbccddeeff"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
