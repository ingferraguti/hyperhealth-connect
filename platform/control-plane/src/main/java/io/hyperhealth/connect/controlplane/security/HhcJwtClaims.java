package io.hyperhealth.connect.controlplane.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.security.oauth2.jwt.Jwt;

import io.hyperhealth.connect.controlplane.inventory.InventoryId.FacilityId;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.TenantId;
import io.hyperhealth.connect.controlplane.inventory.VerifiedFacilityScope;

/** Strict parser for the private claims in an HHC access token. */
final class HhcJwtClaims {

    static final String PRINCIPAL_TYPE = "hhc_principal_type";
    static final String ROLES = "hhc_roles";
    static final String TENANT_ID = "hhc_tenant_id";
    static final String FACILITY_ID = "hhc_facility_id";
    static final String AUTHORIZED_PARTY = "azp";

    private HhcJwtClaims() {}

    static AuthenticatedIdentity parse(Jwt jwt) {
        Map<String, Object> claims = jwt.getClaims();
        String subject = strictString(claims, "sub");
        String authorizedParty = strictString(claims, AUTHORIZED_PARTY);
        PrincipalType principalType;
        try {
            principalType = PrincipalType.valueOf(strictString(claims, PRINCIPAL_TYPE));
        } catch (IllegalArgumentException exception) {
            throw new InvalidIdentityClaimException("Invalid principal type", exception);
        }

        Object rawRoles = claims.get(ROLES);
        if (!(rawRoles instanceof Collection<?> roleValues) || roleValues.isEmpty()) {
            throw new InvalidIdentityClaimException("Roles must be a non-empty string array");
        }
        Set<ControlPlaneRole> roles = new LinkedHashSet<>();
        for (Object rawRole : roleValues) {
            if (!(rawRole instanceof String value) || value.isBlank()) {
                throw new InvalidIdentityClaimException("Roles must be a non-empty string array");
            }
            ControlPlaneRole role = ControlPlaneRole.fromClaim(value)
                    .orElseThrow(() -> new InvalidIdentityClaimException("Unknown role"));
            if (role.principalType() != principalType || !roles.add(role)) {
                throw new InvalidIdentityClaimException("Role is invalid for the principal type");
            }
        }

        try {
            VerifiedFacilityScope scope = new VerifiedFacilityScope(
                    TenantId.parse(strictString(claims, TENANT_ID)),
                    FacilityId.parse(strictString(claims, FACILITY_ID)));
            return new AuthenticatedIdentity(subject, authorizedParty, principalType, roles, scope);
        } catch (IllegalArgumentException exception) {
            throw new InvalidIdentityClaimException("Invalid tenant or facility identifier", exception);
        }
    }

    private static String strictString(Map<String, Object> claims, String name) {
        Object value = claims.get(name);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new InvalidIdentityClaimException("Missing or invalid claim: " + name);
        }
        return text;
    }

    static final class InvalidIdentityClaimException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        InvalidIdentityClaimException(String message) {
            super(message);
        }

        InvalidIdentityClaimException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
