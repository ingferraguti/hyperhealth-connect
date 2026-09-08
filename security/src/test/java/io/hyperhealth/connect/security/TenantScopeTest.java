package io.hyperhealth.connect.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TenantScopeTest {

    @Test
    void deniesCrossTenantAccessBeforeResourceUse() {
        var caller = new TenantScope("tenant-a", "organization-a", "facility-a");
        var resource = new TenantScope("tenant-b", "organization-a", "facility-a");

        assertThrows(CrossTenantAccessException.class, () -> caller.requireContains(resource));
    }

    @Test
    void deniesCrossFacilityAccessWithinTenant() {
        var caller = new TenantScope("tenant-a", "organization-a", "facility-a");
        var resource = new TenantScope("tenant-a", "organization-a", "facility-b");

        assertThrows(CrossTenantAccessException.class, () -> caller.requireContains(resource));
    }

    @Test
    void acceptsExactScopeOnly() {
        var caller = new TenantScope("tenant-a", "organization-a", "facility-a");
        assertDoesNotThrow(() -> caller.requireContains(caller));
    }
}

