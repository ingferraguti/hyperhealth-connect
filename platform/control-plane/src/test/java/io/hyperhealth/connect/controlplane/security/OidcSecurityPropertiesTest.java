package io.hyperhealth.connect.controlplane.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.time.Duration;

import org.junit.jupiter.api.Test;

class OidcSecurityPropertiesTest {

    @Test
    void acceptsSeparateHttpsTrustProfiles() {
        assertThatCode(validProperties()::validateEnabledConfiguration).doesNotThrowAnyException();
    }

    @Test
    void allowsAnUnconfiguredDisabledBoundaryButNeverAnEnabledOne() {
        OidcSecurityProperties disabled = new OidcSecurityProperties(false, "", "", null, null, null, null);
        assertThatCode(disabled::validateEnabledConfiguration).doesNotThrowAnyException();

        OidcSecurityProperties enabled = new OidcSecurityProperties(true, "", "", null, null, null, null);
        assertThatThrownBy(enabled::validateEnabledConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("issuer-uri");
    }

    @Test
    void rejectsInsecureUrisAndOverlappingHumanWorkloadTrust() {
        OidcSecurityProperties insecure = new OidcSecurityProperties(
                true,
                "http://idp.example.test/realms/hhc",
                "https://idp.example.test/realms/hhc/protocol/openid-connect/certs",
                Duration.ofSeconds(2),
                Duration.ofSeconds(2),
                profile("human", "human-client"),
                profile("workload", "workload-client"));
        assertThatThrownBy(insecure::validateEnabledConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTPS");

        OidcSecurityProperties sharedAudience = new OidcSecurityProperties(
                true,
                "https://idp.example.test/realms/hhc",
                "https://idp.example.test/realms/hhc/protocol/openid-connect/certs",
                Duration.ofSeconds(2),
                Duration.ofSeconds(2),
                profile("shared", "human-client"),
                profile("shared", "workload-client"));
        assertThatThrownBy(sharedAudience::validateEnabledConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("audiences");

        OidcSecurityProperties sharedClient = new OidcSecurityProperties(
                true,
                "https://idp.example.test/realms/hhc",
                "https://idp.example.test/realms/hhc/protocol/openid-connect/certs",
                Duration.ofSeconds(2),
                Duration.ofSeconds(2),
                profile("human", "shared-client"),
                profile("workload", "shared-client"));
        assertThatThrownBy(sharedClient::validateEnabledConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disjoint");
    }

    static OidcSecurityProperties validProperties() {
        return new OidcSecurityProperties(
                true,
                "https://idp.example.test/realms/hhc",
                "https://idp.example.test/realms/hhc/protocol/openid-connect/certs",
                Duration.ofSeconds(2),
                Duration.ofSeconds(2),
                new OidcSecurityProperties.TokenProfile(
                        "hhc-control-plane-human", List.of("hhc-control-plane-ui"), Duration.ofMinutes(10)),
                new OidcSecurityProperties.TokenProfile(
                        "hhc-control-plane-workload", List.of("hhc-runtime-agent"), Duration.ofMinutes(5)));
    }

    private static OidcSecurityProperties.TokenProfile profile(String audience, String party) {
        return new OidcSecurityProperties.TokenProfile(audience, List.of(party), Duration.ofMinutes(5));
    }
}
