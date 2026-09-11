package io.hyperhealth.connect.controlplane.inventory;

import java.util.List;
import java.util.Objects;

import io.hyperhealth.connect.controlplane.inventory.InventoryId.ApplicationId;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.EndpointId;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.FacilityId;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.OrganizationId;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.TenantId;

/**
 * Immutable path through the normative inventory hierarchy.
 *
 * <p>Every non-root path owns its complete ancestry. A missing intermediate level therefore cannot
 * be represented. Runtime cells deliberately do not implement this interface: they are technical
 * failure/scaling domains associated with one or more paths, not children of an endpoint.</p>
 */
public sealed interface InventoryPath
        permits InventoryPath.TenantPath,
                InventoryPath.OrganizationPath,
                InventoryPath.FacilityPath,
                InventoryPath.ApplicationPath,
                InventoryPath.EndpointPath {

    TenantId tenantId();

    List<InventoryId> segments();

    /** Returns true when this path is equal to, or an ancestor of, the candidate path. */
    default boolean contains(InventoryPath candidate) {
        Objects.requireNonNull(candidate, "candidate");
        List<InventoryId> owner = segments();
        List<InventoryId> requested = candidate.segments();
        return owner.size() <= requested.size()
                && owner.equals(requested.subList(0, owner.size()));
    }

    record TenantPath(TenantId tenantId) implements InventoryPath {
        public TenantPath {
            Objects.requireNonNull(tenantId, "tenantId");
        }

        @Override
        public List<InventoryId> segments() {
            return List.of(tenantId);
        }
    }

    record OrganizationPath(TenantPath parent, OrganizationId organizationId)
            implements InventoryPath {
        public OrganizationPath {
            Objects.requireNonNull(parent, "parent");
            Objects.requireNonNull(organizationId, "organizationId");
        }

        @Override
        public TenantId tenantId() {
            return parent.tenantId();
        }

        @Override
        public List<InventoryId> segments() {
            return List.of(tenantId(), organizationId);
        }
    }

    record FacilityPath(OrganizationPath parent, FacilityId facilityId) implements InventoryPath {
        public FacilityPath {
            Objects.requireNonNull(parent, "parent");
            Objects.requireNonNull(facilityId, "facilityId");
        }

        @Override
        public TenantId tenantId() {
            return parent.tenantId();
        }

        @Override
        public List<InventoryId> segments() {
            return List.of(tenantId(), parent.organizationId(), facilityId);
        }
    }

    record ApplicationPath(FacilityPath parent, ApplicationId applicationId)
            implements InventoryPath {
        public ApplicationPath {
            Objects.requireNonNull(parent, "parent");
            Objects.requireNonNull(applicationId, "applicationId");
        }

        @Override
        public TenantId tenantId() {
            return parent.tenantId();
        }

        @Override
        public List<InventoryId> segments() {
            return List.of(
                    tenantId(),
                    parent.parent().organizationId(),
                    parent.facilityId(),
                    applicationId);
        }
    }

    record EndpointPath(ApplicationPath parent, EndpointId endpointId) implements InventoryPath {
        public EndpointPath {
            Objects.requireNonNull(parent, "parent");
            Objects.requireNonNull(endpointId, "endpointId");
        }

        @Override
        public TenantId tenantId() {
            return parent.tenantId();
        }

        @Override
        public List<InventoryId> segments() {
            FacilityPath facility = parent.parent();
            return List.of(
                    tenantId(),
                    facility.parent().organizationId(),
                    facility.facilityId(),
                    parent.applicationId(),
                    endpointId);
        }
    }
}
