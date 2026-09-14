package io.hyperhealth.connect.controlplane.security;

import java.net.URI;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration contract for the human and workload OIDC trust profiles. */
@ConfigurationProperties("hhc.security.oidc")
public record OidcSecurityProperties(
        boolean enabled,
        String issuerUri,
        String jwkSetUri,
        Duration jwkConnectTimeout,
        Duration jwkReadTimeout,
        TokenProfile human,
        TokenProfile workload) {

    public OidcSecurityProperties {
        human = human == null ? new TokenProfile(null, List.of(), null) : human;
        workload = workload == null ? new TokenProfile(null, List.of(), null) : workload;
        jwkConnectTimeout = jwkConnectTimeout == null ? Duration.ofSeconds(2) : jwkConnectTimeout;
        jwkReadTimeout = jwkReadTimeout == null ? Duration.ofSeconds(2) : jwkReadTimeout;
    }

    /** Validates all trust-boundary invariants before an enabled resource server is built. */
    void validateEnabledConfiguration() {
        if (!enabled) {
            return;
        }
        requireHttpsUri(issuerUri, "issuer-uri");
        requireHttpsUri(jwkSetUri, "jwk-set-uri");
        requireBoundedTimeout(jwkConnectTimeout, "jwk-connect-timeout");
        requireBoundedTimeout(jwkReadTimeout, "jwk-read-timeout");
        human.validate("human");
        workload.validate("workload");
        if (human.audience().equals(workload.audience())) {
            throw new IllegalStateException("Human and workload audiences must be different");
        }
        Set<String> overlap = new HashSet<>(human.authorizedParties());
        overlap.retainAll(workload.authorizedParties());
        if (!overlap.isEmpty()) {
            throw new IllegalStateException("Human and workload authorized parties must be disjoint");
        }
    }

    private static void requireBoundedTimeout(Duration value, String name) {
        if (value.compareTo(Duration.ofMillis(100)) < 0 || value.compareTo(Duration.ofSeconds(10)) > 0) {
            throw new IllegalStateException(name + " must be between 100ms and 10s");
        }
    }

    private static void requireHttpsUri(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be configured when OIDC is enabled");
        }
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(name + " must be an absolute HTTPS URI", exception);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getFragment() != null) {
            throw new IllegalStateException(name + " must be an absolute HTTPS URI without a fragment");
        }
    }

    /** Audience and client allowlist for exactly one principal class. */
    public record TokenProfile(String audience, List<String> authorizedParties, Duration maxTokenLifetime) {
        public TokenProfile {
            authorizedParties = authorizedParties == null ? List.of() : List.copyOf(authorizedParties);
        }

        private void validate(String profileName) {
            requireText(audience, profileName + ".audience");
            if (authorizedParties.isEmpty()) {
                throw new IllegalStateException(profileName + ".authorized-parties must not be empty");
            }
            if (maxTokenLifetime == null
                    || maxTokenLifetime.compareTo(Duration.ofMinutes(1)) < 0
                    || maxTokenLifetime.compareTo(Duration.ofHours(1)) > 0) {
                throw new IllegalStateException(profileName + ".max-token-lifetime must be between 1m and 1h");
            }
            Set<String> unique = new HashSet<>();
            for (String party : authorizedParties) {
                requireText(party, profileName + ".authorized-parties entry");
                if (!unique.add(party)) {
                    throw new IllegalStateException(profileName + ".authorized-parties contains a duplicate");
                }
            }
        }

        private static void requireText(String value, String name) {
            Objects.requireNonNull(name, "name");
            if (value == null || value.isBlank()) {
                throw new IllegalStateException(name + " must not be blank");
            }
        }
    }
}
