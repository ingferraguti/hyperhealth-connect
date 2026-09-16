package io.hyperhealth.connect.controlplane.security;

import java.util.EnumSet;
import java.util.ArrayList;
import java.util.List;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/** Maps only validated closed-set roles to internal capabilities. */
final class HhcJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    static final String INVENTORY_READ_AUTHORITY = "HHC_INVENTORY_READ";
    static final String INVENTORY_WRITE_AUTHORITY = "HHC_INVENTORY_WRITE";
    private static final EnumSet<ControlPlaneRole> INVENTORY_READ_ROLES = EnumSet.of(
            ControlPlaneRole.FACILITY_OPERATOR,
            ControlPlaneRole.AUDITOR,
            ControlPlaneRole.RUNTIME_AGENT);

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        AuthenticatedIdentity identity = HhcJwtClaims.parse(jwt);
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        if (identity.roles().stream().anyMatch(INVENTORY_READ_ROLES::contains)) {
            authorities.add(new SimpleGrantedAuthority(INVENTORY_READ_AUTHORITY));
        }
        if (identity.roles().contains(ControlPlaneRole.FACILITY_OPERATOR)) {
            authorities.add(new SimpleGrantedAuthority(INVENTORY_WRITE_AUTHORITY));
        }
        return new HhcJwtAuthenticationToken(jwt, List.copyOf(authorities), identity);
    }
}
