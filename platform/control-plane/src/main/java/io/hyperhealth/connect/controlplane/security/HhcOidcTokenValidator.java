package io.hyperhealth.connect.controlplane.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/** Validates audience/client separation and the strict HHC private-claim contract. */
final class HhcOidcTokenValidator implements OAuth2TokenValidator<Jwt> {

    static final Duration ALLOWED_CLOCK_SKEW = Duration.ofSeconds(60);

    private static final OAuth2Error INVALID_TOKEN = new OAuth2Error(
            "invalid_token", "The token does not satisfy the HHC identity contract", null);

    private final OidcSecurityProperties properties;
    private final Clock clock;

    HhcOidcTokenValidator(OidcSecurityProperties properties) {
        this(properties, Clock.systemUTC());
    }

    HhcOidcTokenValidator(OidcSecurityProperties properties, Clock clock) {
        this.properties = java.util.Objects.requireNonNull(properties, "properties");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        try {
            AuthenticatedIdentity identity = HhcJwtClaims.parse(jwt);
            OidcSecurityProperties.TokenProfile expected = identity.principalType() == PrincipalType.HUMAN
                    ? properties.human()
                    : properties.workload();
            OidcSecurityProperties.TokenProfile other = identity.principalType() == PrincipalType.HUMAN
                    ? properties.workload()
                    : properties.human();
            Instant issuedAt = jwt.getIssuedAt();
            Instant expiresAt = jwt.getExpiresAt();
            if (issuedAt == null
                    || expiresAt == null
                    || !expiresAt.isAfter(issuedAt)
                    || issuedAt.isAfter(Instant.now(clock).plus(ALLOWED_CLOCK_SKEW))
                    || Duration.between(issuedAt, expiresAt).compareTo(expected.maxTokenLifetime()) > 0) {
                return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
            }
            List<String> audiences = jwt.getAudience();
            if (audiences == null
                    || !audiences.contains(expected.audience())
                    || audiences.contains(other.audience())
                    || !expected.authorizedParties().contains(identity.authorizedParty())) {
                return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
            }
            return OAuth2TokenValidatorResult.success();
        } catch (IllegalArgumentException exception) {
            return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
        }
    }
}
