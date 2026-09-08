package io.hyperhealth.connect.security;

import java.util.Objects;

/** Security scope propagated at every internal boundary. */
public record TenantScope(String tenantId, String organizationId, String facilityId) {

    public TenantScope {
        tenantId = requireText(tenantId, "tenantId");
        organizationId = requireText(organizationId, "organizationId");
        facilityId = requireText(facilityId, "facilityId");
    }

    public void requireSameTenant(TenantScope resourceScope) {
        Objects.requireNonNull(resourceScope, "resourceScope");
        if (!tenantId.equals(resourceScope.tenantId)) {
            throw new CrossTenantAccessException("Cross-tenant access denied");
        }
    }

    public void requireContains(TenantScope resourceScope) {
        requireSameTenant(resourceScope);
        if (!organizationId.equals(resourceScope.organizationId)
                || !facilityId.equals(resourceScope.facilityId)) {
            throw new CrossTenantAccessException("Cross-scope access denied");
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}

