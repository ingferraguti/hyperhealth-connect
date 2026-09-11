package io.hyperhealth.connect.controlplane.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class RuntimeCellTest {

    @Test
    void facilityAssignmentAuthorizesOnlyThatFacilityAndItsDescendants() {
        Tenant tenant = Tenant.create();
        Organization organization = Organization.createUnder(tenant);
        Facility authorizedFacility = Facility.createUnder(organization);
        Facility otherFacility = Facility.createUnder(organization);
        Endpoint authorizedEndpoint = Endpoint.createUnder(Application.createUnder(authorizedFacility));
        Endpoint otherEndpoint = Endpoint.createUnder(Application.createUnder(otherFacility));
        RuntimeCell cell = RuntimeCell.create(Set.of(authorizedFacility.path()));

        assertThat(cell.isAuthorizedFor(authorizedFacility)).isTrue();
        assertThat(cell.isAuthorizedFor(authorizedEndpoint)).isTrue();
        assertThat(cell.isAuthorizedFor(otherFacility)).isFalse();
        assertThat(cell.isAuthorizedFor(otherEndpoint)).isFalse();
        assertThat(cell.isAuthorizedFor(tenant)).isFalse();
    }

    @Test
    void multiCompanyAssignmentsAreExplicitAndDoNotWidenEachOther() {
        Tenant tenantA = Tenant.create();
        Tenant tenantB = Tenant.create();
        Facility facilityA = Facility.createUnder(Organization.createUnder(tenantA));
        Facility facilityB = Facility.createUnder(Organization.createUnder(tenantB));
        Facility unassignedFacilityB = Facility.createUnder(Organization.createUnder(tenantB));
        RuntimeCell cell = RuntimeCell.create(Set.of(facilityA.path(), facilityB.path()));

        assertThat(cell.isAuthorizedFor(facilityA)).isTrue();
        assertThat(cell.isAuthorizedFor(facilityB)).isTrue();
        assertThat(cell.isAuthorizedFor(unassignedFacilityB)).isFalse();
    }

    @Test
    void assignmentsAreDefensivelyCopiedAndUnmodifiable() {
        Set<InventoryPath> source = new HashSet<>();
        source.add(Tenant.create().path());
        RuntimeCell cell = RuntimeCell.create(source);

        source.clear();

        assertThat(cell.authorizedScopes()).hasSize(1);
        assertThatThrownBy(() -> cell.authorizedScopes().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void cellWithoutAValidAssignmentIsRejected() {
        assertThatThrownBy(() -> RuntimeCell.create(Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one");
        assertThatThrownBy(() -> new RuntimeCell(InventoryId.RuntimeCellId.newId(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("authorizedScopes");
    }
}
