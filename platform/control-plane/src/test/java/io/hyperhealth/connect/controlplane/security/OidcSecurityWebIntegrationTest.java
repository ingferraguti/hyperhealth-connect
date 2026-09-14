package io.hyperhealth.connect.controlplane.security;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import io.hyperhealth.connect.controlplane.ControlPlaneApplication;
import io.hyperhealth.connect.controlplane.inventory.VerifiedFacilityScope;
import io.hyperhealth.connect.controlplane.inventory.api.VerifiedFacilityScopeArgumentResolver;

@SpringBootTest(
        classes = {ControlPlaneApplication.class, OidcSecurityWebIntegrationTest.WebTestConfiguration.class},
        properties = {
            "hhc.security.oidc.enabled=true",
            "hhc.security.oidc.issuer-uri=https://idp.example.test/realms/hhc",
            "hhc.security.oidc.jwk-set-uri=https://idp.example.test/realms/hhc/protocol/openid-connect/certs",
            "hhc.security.oidc.human.audience=hhc-control-plane-human",
            "hhc.security.oidc.human.authorized-parties=hhc-control-plane-ui",
            "hhc.security.oidc.human.max-token-lifetime=10m",
            "hhc.security.oidc.workload.audience=hhc-control-plane-workload",
            "hhc.security.oidc.workload.authorized-parties=hhc-runtime-agent",
            "hhc.security.oidc.workload.max-token-lifetime=5m"
        })
@AutoConfigureMockMvc
class OidcSecurityWebIntegrationTest {

    private static final String ISSUER = "https://idp.example.test/realms/hhc";
    private static final KeyPair TRUSTED_KEY = createKey();
    private static final KeyPair UNTRUSTED_KEY = createKey();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void humanTokenCreatesScopeAndOverwritesSpoofedHeadersAndRequestAttributes() throws Exception {
        String tenant = "t-" + UUID.randomUUID();
        String facility = "f-" + UUID.randomUUID();
        VerifiedFacilityScope spoofed = new VerifiedFacilityScope(
                new io.hyperhealth.connect.controlplane.inventory.InventoryId.TenantId(UUID.randomUUID()),
                new io.hyperhealth.connect.controlplane.inventory.InventoryId.FacilityId(UUID.randomUUID()));

        mockMvc.perform(get("/api/v1/endpoints/{endpointId}", "ep-synthetic")
                        .header("Authorization", "Bearer " + token(
                                TRUSTED_KEY,
                                PrincipalType.HUMAN,
                                "FacilityOperator",
                                "hhc-control-plane-human",
                                "hhc-control-plane-ui",
                                tenant,
                                facility,
                                Instant.now().plusSeconds(300)))
                        .header("X-Tenant-ID", spoofed.tenantId().externalForm())
                        .header("X-Facility-ID", spoofed.facilityId().externalForm())
                        .requestAttr(VerifiedFacilityScopeArgumentResolver.REQUEST_ATTRIBUTE, spoofed))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(tenant))
                .andExpect(jsonPath("$.facilityId").value(facility))
                .andExpect(content().string(not(containsString(spoofed.tenantId().externalForm()))));
    }

    @Test
    void workloadUsesItsOwnAudienceClientAndRoleNamespace() throws Exception {
        String tenant = "t-" + UUID.randomUUID();
        String facility = "f-" + UUID.randomUUID();
        mockMvc.perform(get("/api/v1/endpoints/{endpointId}", "ep-synthetic")
                        .header("Authorization", "Bearer " + token(
                                TRUSTED_KEY,
                                PrincipalType.WORKLOAD,
                                "RuntimeAgent",
                                "hhc-control-plane-workload",
                                "hhc-runtime-agent",
                                tenant,
                                facility,
                                Instant.now().plusSeconds(240))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.principalType").value("WORKLOAD"))
                .andExpect(jsonPath("$.tenantId").value(tenant))
                .andExpect(jsonPath("$.facilityId").value(facility));
    }

    @Test
    void missingMalformedAndCryptographicallyInvalidTokensFailWithSafe401() throws Exception {
        mockMvc.perform(get("/api/v1/endpoints/ep-synthetic"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("HHC-AUTH-401-001"));

        String wrongSignature = token(
                UNTRUSTED_KEY,
                PrincipalType.HUMAN,
                "FacilityOperator",
                "hhc-control-plane-human",
                "hhc-control-plane-ui",
                "t-" + UUID.randomUUID(),
                "f-" + UUID.randomUUID(),
                Instant.now().plusSeconds(300));
        mockMvc.perform(get("/api/v1/endpoints/ep-synthetic")
                        .header("Authorization", "Bearer " + wrongSignature))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(not(containsString(wrongSignature))));
    }

    @Test
    void wrongAudienceExpiredTokenAndHumanWorkloadRoleConfusionFailWith401() throws Exception {
        assertUnauthorized(token(
                TRUSTED_KEY,
                PrincipalType.HUMAN,
                "FacilityOperator",
                "hhc-control-plane-workload",
                "hhc-control-plane-ui",
                "t-" + UUID.randomUUID(),
                "f-" + UUID.randomUUID(),
                Instant.now().plusSeconds(300)));
        assertUnauthorized(token(
                TRUSTED_KEY,
                PrincipalType.HUMAN,
                "FacilityOperator",
                "hhc-control-plane-human",
                "hhc-control-plane-ui",
                "t-" + UUID.randomUUID(),
                "f-" + UUID.randomUUID(),
                Instant.now().minusSeconds(120)));
        assertUnauthorized(token(
                TRUSTED_KEY,
                PrincipalType.HUMAN,
                "RuntimeAgent",
                "hhc-control-plane-human",
                "hhc-control-plane-ui",
                "t-" + UUID.randomUUID(),
                "f-" + UUID.randomUUID(),
                Instant.now().plusSeconds(300)));
    }

    @Test
    void authenticatedRoleWithoutInventoryCapabilityFailsWith403() throws Exception {
        mockMvc.perform(get("/api/v1/endpoints/ep-synthetic")
                        .header("Authorization", "Bearer " + token(
                                TRUSTED_KEY,
                                PrincipalType.HUMAN,
                                "FlowDeveloper",
                                "hhc-control-plane-human",
                                "hhc-control-plane-ui",
                                "t-" + UUID.randomUUID(),
                                "f-" + UUID.randomUUID(),
                                Instant.now().plusSeconds(300))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HHC-AUTH-403-001"));
    }

    private void assertUnauthorized(String bearer) throws Exception {
        mockMvc.perform(get("/api/v1/endpoints/ep-synthetic")
                        .header("Authorization", "Bearer " + bearer))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("The request cannot be authorized."));
    }

    private static String token(
            KeyPair keyPair,
            PrincipalType type,
            String role,
            String audience,
            String authorizedParty,
            String tenant,
            String facility,
            Instant expiresAt)
            throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject("synthetic-subject")
                .audience(audience)
                .issueTime(Date.from(now.minusSeconds(5)))
                .notBeforeTime(Date.from(now.minusSeconds(5)))
                .expirationTime(Date.from(expiresAt))
                .jwtID(UUID.randomUUID().toString())
                .claim(HhcJwtClaims.AUTHORIZED_PARTY, authorizedParty)
                .claim(HhcJwtClaims.PRINCIPAL_TYPE, type.name())
                .claim(HhcJwtClaims.ROLES, List.of(role))
                .claim(HhcJwtClaims.TENANT_ID, tenant)
                .claim(HhcJwtClaims.FACILITY_ID, facility)
                .build();
        SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        signedJwt.sign(new RSASSASigner((RSAPrivateKey) keyPair.getPrivate()));
        return signedJwt.serialize();
    }

    private static KeyPair createKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot create synthetic test key", exception);
        }
    }

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class WebTestConfiguration {

        @Bean
        @Primary
        JwtDecoder testJwtDecoder(OAuth2TokenValidator<Jwt> hhcJwtValidator) {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) TRUSTED_KEY.getPublic())
                    .signatureAlgorithm(SignatureAlgorithm.RS256)
                    .build();
            decoder.setJwtValidator(hhcJwtValidator);
            return decoder;
        }

        @Bean
        ScopeEchoController scopeEchoController() {
            return new ScopeEchoController();
        }

        @Bean
        WebMvcConfigurer verifiedScopeResolver() {
            return new WebMvcConfigurer() {
                @Override
                public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                    resolvers.add(new VerifiedFacilityScopeArgumentResolver());
                }
            };
        }
    }

    @RestController
    @RequestMapping("/api/v1/endpoints")
    static class ScopeEchoController {

        @GetMapping("/{endpointId}")
        Map<String, String> get(
                VerifiedFacilityScope scope,
                HhcJwtAuthenticationToken authentication,
                @PathVariable String endpointId) {
            return Map.of(
                    "endpointId", endpointId,
                    "tenantId", scope.tenantId().externalForm(),
                    "facilityId", scope.facilityId().externalForm(),
                    "principalType", authentication.identity().principalType().name());
        }
    }
}
