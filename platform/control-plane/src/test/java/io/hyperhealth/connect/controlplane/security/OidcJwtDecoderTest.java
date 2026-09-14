package io.hyperhealth.connect.controlplane.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

class OidcJwtDecoderTest {

    private static KeyPair trustedKey;
    private static KeyPair untrustedKey;

    private final OidcSecurityProperties properties = OidcSecurityPropertiesTest.validProperties();

    @BeforeAll
    static void createKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        trustedKey = generator.generateKeyPair();
        untrustedKey = generator.generateKeyPair();
    }

    @Test
    void verifiesSignatureIssuerLifetimeAndIdentityContract() throws Exception {
        JwtDecoder decoder = decoder((RSAPublicKey) trustedKey.getPublic());
        String valid = signedToken(
                trustedKey,
                properties.issuerUri(),
                Instant.now().plusSeconds(300),
                "hhc-control-plane-human",
                "hhc-control-plane-ui",
                PrincipalType.HUMAN,
                "FacilityOperator");

        assertThat(decoder.decode(valid).getSubject()).isEqualTo("synthetic-subject");

        assertThatThrownBy(() -> decoder.decode(signedToken(
                        untrustedKey,
                        properties.issuerUri(),
                        Instant.now().plusSeconds(300),
                        "hhc-control-plane-human",
                        "hhc-control-plane-ui",
                        PrincipalType.HUMAN,
                        "FacilityOperator")))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(signedToken(
                        trustedKey,
                        "https://attacker.example.test/realms/hhc",
                        Instant.now().plusSeconds(300),
                        "hhc-control-plane-human",
                        "hhc-control-plane-ui",
                        PrincipalType.HUMAN,
                        "FacilityOperator")))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(signedToken(
                        trustedKey,
                        properties.issuerUri(),
                        Instant.now().minusSeconds(120),
                        "hhc-control-plane-human",
                        "hhc-control-plane-ui",
                        PrincipalType.HUMAN,
                        "FacilityOperator")))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsConfusedDeputyProfilesAfterCryptographicVerification() throws Exception {
        JwtDecoder decoder = decoder((RSAPublicKey) trustedKey.getPublic());
        assertThatThrownBy(() -> decoder.decode(signedToken(
                        trustedKey,
                        properties.issuerUri(),
                        Instant.now().plusSeconds(300),
                        "hhc-control-plane-workload",
                        "hhc-runtime-agent",
                        PrincipalType.HUMAN,
                        "FacilityOperator")))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(signedToken(
                        trustedKey,
                        properties.issuerUri(),
                        Instant.now().plusSeconds(300),
                        "hhc-control-plane-human",
                        "hhc-control-plane-ui",
                        PrincipalType.WORKLOAD,
                        "RuntimeAgent")))
                .isInstanceOf(JwtException.class);
    }

    private JwtDecoder decoder(RSAPublicKey publicKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(new OidcSecurityConfiguration().hhcJwtValidator(properties));
        return decoder;
    }

    static String signedToken(
            KeyPair keyPair,
            String issuer,
            Instant expiresAt,
            String audience,
            String authorizedParty,
            PrincipalType type,
            String role)
            throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject("synthetic-subject")
                .audience(audience)
                .issueTime(Date.from(now.minusSeconds(5)))
                .notBeforeTime(Date.from(now.minusSeconds(5)))
                .expirationTime(Date.from(expiresAt))
                .jwtID(UUID.randomUUID().toString())
                .claim(HhcJwtClaims.AUTHORIZED_PARTY, authorizedParty)
                .claim(HhcJwtClaims.PRINCIPAL_TYPE, type.name())
                .claim(HhcJwtClaims.ROLES, List.of(role))
                .claim(HhcJwtClaims.TENANT_ID, "t-" + UUID.randomUUID())
                .claim(HhcJwtClaims.FACILITY_ID, "f-" + UUID.randomUUID())
                .build();
        SignedJWT token = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        token.sign(new RSASSASigner((RSAPrivateKey) keyPair.getPrivate()));
        return token.serialize();
    }
}
