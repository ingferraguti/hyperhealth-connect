package io.hyperhealth.connect.controlplane.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class InventoryHierarchyTest {

    @Test
    void endpointCarriesItsCompleteImmutableAncestry() {
        Tenant tenant = Tenant.create();
        Organization organization = Organization.createUnder(tenant);
        Facility facility = Facility.createUnder(organization);
        Application application = Application.createUnder(facility);
        Endpoint endpoint = Endpoint.createUnder(application);

        assertThat(endpoint.path().tenantId()).isEqualTo(tenant.id());
        assertThat(endpoint.path().segments())
                .containsExactly(
                        tenant.id(),
                        organization.id(),
                        facility.id(),
                        application.id(),
                        endpoint.id());
        assertThat(endpoint.path().segments()).isUnmodifiable();
    }

    @Test
    void everyAncestorContainsItsDescendantsButNotItsSiblings() {
        Tenant tenant = Tenant.create();
        Organization organization = Organization.createUnder(tenant);
        Facility facilityA = Facility.createUnder(organization);
        Facility facilityB = Facility.createUnder(organization);
        Endpoint endpoint = Endpoint.createUnder(Application.createUnder(facilityA));

        assertThat(tenant.path().contains(endpoint.path())).isTrue();
        assertThat(organization.path().contains(endpoint.path())).isTrue();
        assertThat(facilityA.path().contains(endpoint.path())).isTrue();
        assertThat(facilityB.path().contains(endpoint.path())).isFalse();
        assertThat(endpoint.path().contains(facilityA.path())).isFalse();
    }

    @Test
    void aDifferentTenantNeverContainsTheRequestedResource() {
        Tenant tenantA = Tenant.create();
        Tenant tenantB = Tenant.create();
        Endpoint endpointA = Endpoint.createUnder(
                Application.createUnder(Facility.createUnder(Organization.createUnder(tenantA))));

        assertThat(tenantB.path().contains(endpointA.path())).isFalse();
    }

    @Test
    void missingParentCannotBeRepresented() {
        assertThatThrownBy(() -> Organization.createUnder(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("parent");
        assertThatThrownBy(() -> new InventoryPath.OrganizationPath(null, InventoryId.OrganizationId.newId()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("parent");
    }

    @Test
    void returnedSegmentsCannotMutateAPath() {
        InventoryPath path = Tenant.create().path();
        List<InventoryId> segments = path.segments();

        assertThatThrownBy(() -> segments.add(InventoryId.TenantId.newId()))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
