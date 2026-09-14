package io.hyperhealth.connect.controlplane.security;

import java.io.Serial;
import java.util.Collection;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Authenticated bearer token retaining the validated HHC identity and facility scope. */
final class HhcJwtAuthenticationToken extends JwtAuthenticationToken {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient AuthenticatedIdentity identity;

    HhcJwtAuthenticationToken(
            Jwt jwt, Collection<? extends GrantedAuthority> authorities, AuthenticatedIdentity identity) {
        super(jwt, authorities, identity.subject());
        this.identity = java.util.Objects.requireNonNull(identity, "identity");
    }

    AuthenticatedIdentity identity() {
        return java.util.Objects.requireNonNull(identity, "identity is unavailable after serialization");
    }
}
