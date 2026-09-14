package io.hyperhealth.connect.controlplane.security;

import java.util.Set;

import io.hyperhealth.connect.controlplane.inventory.VerifiedFacilityScope;

/** Immutable identity and scope produced only after JWT validation succeeds. */
public record AuthenticatedIdentity(
        String subject,
        String authorizedParty,
        PrincipalType principalType,
        Set<ControlPlaneRole> roles,
        VerifiedFacilityScope facilityScope) {

    public AuthenticatedIdentity {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("subject must not be blank");
        }
        if (authorizedParty == null || authorizedParty.isBlank()) {
            throw new IllegalArgumentException("authorizedParty must not be blank");
        }
        java.util.Objects.requireNonNull(principalType, "principalType");
        roles = Set.copyOf(roles);
        if (roles.isEmpty()) {
            throw new IllegalArgumentException("roles must not be empty");
        }
        java.util.Objects.requireNonNull(facilityScope, "facilityScope");
    }
}
