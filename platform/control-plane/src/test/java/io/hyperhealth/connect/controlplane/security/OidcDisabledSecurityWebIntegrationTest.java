package io.hyperhealth.connect.controlplane.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.hyperhealth.connect.controlplane.ControlPlaneApplication;

@SpringBootTest(
        classes = {ControlPlaneApplication.class, OidcDisabledSecurityWebIntegrationTest.TestEndpoint.class},
        properties = "hhc.security.oidc.enabled=false")
@AutoConfigureMockMvc
class OidcDisabledSecurityWebIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void defaultDisabledOidcConfigurationDeniesApiAccess() throws Exception {
        mockMvc.perform(get("/api/v1/endpoints/ep-synthetic")).andExpect(status().isUnauthorized());
    }

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class TestEndpoint {
        @Bean
        DeniedController deniedController() {
            return new DeniedController();
        }
    }

    @RestController
    static class DeniedController {
        @GetMapping("/api/v1/endpoints/{endpointId}")
        String get() {
            return "must-not-be-visible";
        }
    }
}
