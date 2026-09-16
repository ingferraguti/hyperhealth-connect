package io.hyperhealth.connect.controlplane.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/** Fail-closed OIDC resource-server boundary for Control Plane APIs. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(OidcSecurityProperties.class)
public class OidcSecurityConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "hhc.security.oidc", name = "enabled", havingValue = "true")
    OAuth2TokenValidator<Jwt> hhcJwtValidator(OidcSecurityProperties properties) {
        properties.validateEnabledConfiguration();
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuerUri()),
                new HhcOidcTokenValidator(properties));
    }

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    @ConditionalOnProperty(prefix = "hhc.security.oidc", name = "enabled", havingValue = "true")
    JwtDecoder hhcJwtDecoder(
            OidcSecurityProperties properties, OAuth2TokenValidator<Jwt> hhcJwtValidator) {
        properties.validateEnabledConfiguration();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.jwkConnectTimeout());
        requestFactory.setReadTimeout(properties.jwkReadTimeout());
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .restOperations(new RestTemplate(requestFactory))
                .build();
        decoder.setJwtValidator(hhcJwtValidator);
        return decoder;
    }

    @Bean
    SecurityFilterChain hhcSecurityFilterChain(
            HttpSecurity http,
            OidcSecurityProperties properties,
            ObjectProvider<JwtDecoder> decoderProvider)
            throws Exception {
        OidcProblemResponder problems = new OidcProblemResponder();
        http.cors(cors -> cors.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable())
                .logout(logout -> logout.disable())
                .requestCache(cache -> cache.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/endpoints/**")
                        .hasAuthority(HhcJwtAuthenticationConverter.INVENTORY_READ_AUTHORITY)
                        .requestMatchers(HttpMethod.POST, "/api/v1/endpoints")
                        .hasAuthority(HhcJwtAuthenticationConverter.INVENTORY_WRITE_AUTHORITY)
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/endpoints/**")
                        .hasAuthority(HhcJwtAuthenticationConverter.INVENTORY_WRITE_AUTHORITY)
                        .anyRequest()
                        .denyAll());

        if (properties.enabled()) {
            properties.validateEnabledConfiguration();
            JwtDecoder decoder = decoderProvider.getIfAvailable();
            if (decoder == null) {
                throw new IllegalStateException("OIDC is enabled but no JwtDecoder is available");
            }
            http.oauth2ResourceServer(resourceServer -> resourceServer
                            .authenticationEntryPoint(problems)
                            .accessDeniedHandler(problems)
                            .jwt(jwt -> jwt.decoder(decoder)
                                    .jwtAuthenticationConverter(new HhcJwtAuthenticationConverter())))
                    .addFilterAfter(new VerifiedScopePropagationFilter(), BearerTokenAuthenticationFilter.class);
        }
        return http.build();
    }
}
