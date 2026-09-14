package io.hyperhealth.connect.controlplane.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

class HhcOidcTokenValidatorTest {

    private final OidcSecurityProperties properties = OidcSecurityPropertiesTest.validProperties();
    private final HhcOidcTokenValidator validator = new HhcOidcTokenValidator(properties);

    @Test
    void acceptsSeparatedHumanAndWorkloadProfiles() {
        assertThat(validate(token(PrincipalType.HUMAN, "FacilityOperator", "hhc-control-plane-human",
                        "hhc-control-plane-ui")))
                .isTrue();
        assertThat(validate(token(PrincipalType.WORKLOAD, "RuntimeAgent", "hhc-control-plane-workload",
                        "hhc-runtime-agent")))
                .isTrue();
    }

    @Test
    void rejectsWrongOrDualAudienceAndWrongAuthorizedParty() {
        assertThat(validate(token(PrincipalType.HUMAN, "FacilityOperator", "unrelated-api",
                        "hhc-control-plane-ui")))
                .isFalse();
        Jwt dualAudience = baseToken()
                .audience(List.of("hhc-control-plane-human", "hhc-control-plane-workload"))
                .claim(HhcJwtClaims.PRINCIPAL_TYPE, PrincipalType.HUMAN.name())
                .claim(HhcJwtClaims.ROLES, List.of("FacilityOperator"))
                .claim(HhcJwtClaims.AUTHORIZED_PARTY, "hhc-control-plane-ui")
                .build();
        assertThat(validate(dualAudience)).isFalse();
        assertThat(validate(token(PrincipalType.HUMAN, "FacilityOperator", "hhc-control-plane-human",
                        "hhc-runtime-agent")))
                .isFalse();
    }

    @Test
    void rejectsRoleNamespaceConfusionUnknownRolesAndMalformedScope() {
        assertThat(validate(token(PrincipalType.HUMAN, "RuntimeAgent", "hhc-control-plane-human",
                        "hhc-control-plane-ui")))
                .isFalse();
        assertThat(validate(token(PrincipalType.WORKLOAD, "FacilityOperator", "hhc-control-plane-workload",
                        "hhc-runtime-agent")))
                .isFalse();
        assertThat(validate(token(PrincipalType.HUMAN, "SuperAdmin", "hhc-control-plane-human",
                        "hhc-control-plane-ui")))
                .isFalse();

        Jwt malformedScope = baseToken()
                .audience(List.of("hhc-control-plane-human"))
                .claim(HhcJwtClaims.PRINCIPAL_TYPE, PrincipalType.HUMAN.name())
                .claim(HhcJwtClaims.ROLES, List.of("FacilityOperator"))
                .claim(HhcJwtClaims.AUTHORIZED_PARTY, "hhc-control-plane-ui")
                .claim(HhcJwtClaims.TENANT_ID, "tenant-from-an-http-header")
                .build();
        assertThat(validate(malformedScope)).isFalse();
    }

    @Test
    void rejectsMissingOrExcessiveTokenLifetime() {
        Jwt withoutExpiry = Jwt.withTokenValue("synthetic-token")
                .header("alg", "RS256")
                .issuer(properties.issuerUri())
                .subject("synthetic-subject")
                .issuedAt(Instant.now())
                .audience(List.of("hhc-control-plane-human"))
                .claim(HhcJwtClaims.PRINCIPAL_TYPE, PrincipalType.HUMAN.name())
                .claim(HhcJwtClaims.ROLES, List.of("FacilityOperator"))
                .claim(HhcJwtClaims.AUTHORIZED_PARTY, "hhc-control-plane-ui")
                .claim(HhcJwtClaims.TENANT_ID, "t-" + UUID.randomUUID())
                .claim(HhcJwtClaims.FACILITY_ID, "f-" + UUID.randomUUID())
                .build();
        assertThat(validate(withoutExpiry)).isFalse();

        Instant issuedAt = Instant.now();
        Jwt excessiveLifetime = Jwt.withTokenValue("synthetic-token")
                .header("alg", "RS256")
                .issuer(properties.issuerUri())
                .subject("synthetic-subject")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(3_601))
                .audience(List.of("hhc-control-plane-human"))
                .claim(HhcJwtClaims.PRINCIPAL_TYPE, PrincipalType.HUMAN.name())
                .claim(HhcJwtClaims.ROLES, List.of("FacilityOperator"))
                .claim(HhcJwtClaims.AUTHORIZED_PARTY, "hhc-control-plane-ui")
                .claim(HhcJwtClaims.TENANT_ID, "t-" + UUID.randomUUID())
                .claim(HhcJwtClaims.FACILITY_ID, "f-" + UUID.randomUUID())
                .build();
        assertThat(validate(excessiveLifetime)).isFalse();
    }

    @Test
    void mapsOnlyExplicitRolesToTheInventoryReadCapability() {
        HhcJwtAuthenticationConverter converter = new HhcJwtAuthenticationConverter();

        HhcJwtAuthenticationToken operator = (HhcJwtAuthenticationToken) converter.convert(
                token(PrincipalType.HUMAN, "FacilityOperator", "hhc-control-plane-human",
                        "hhc-control-plane-ui"));
        assertThat(operator.getAuthorities())
                .extracting("authority")
                .containsExactly(HhcJwtAuthenticationConverter.INVENTORY_READ_AUTHORITY);
        assertThat(operator.identity().facilityScope().tenantId().externalForm()).startsWith("t-");

        HhcJwtAuthenticationToken developer = (HhcJwtAuthenticationToken) converter.convert(
                token(PrincipalType.HUMAN, "FlowDeveloper", "hhc-control-plane-human",
                        "hhc-control-plane-ui"));
        assertThat(developer.getAuthorities()).isEmpty();
    }

    private boolean validate(Jwt jwt) {
        OAuth2TokenValidatorResult result = validator.validate(jwt);
        return !result.hasErrors();
    }

    private Jwt token(PrincipalType type, String role, String audience, String authorizedParty) {
        return baseToken()
                .audience(List.of(audience))
                .claim(HhcJwtClaims.PRINCIPAL_TYPE, type.name())
                .claim(HhcJwtClaims.ROLES, List.of(role))
                .claim(HhcJwtClaims.AUTHORIZED_PARTY, authorizedParty)
                .build();
    }

    private Jwt.Builder baseToken() {
        Instant now = Instant.now();
        return Jwt.withTokenValue("synthetic-token")
                .header("alg", "RS256")
                .issuer(properties.issuerUri())
                .subject("synthetic-subject")
                .issuedAt(now.minusSeconds(5))
                .expiresAt(now.plusSeconds(240))
                .claim(HhcJwtClaims.TENANT_ID, "t-" + UUID.randomUUID())
                .claim(HhcJwtClaims.FACILITY_ID, "f-" + UUID.randomUUID());
    }
}
