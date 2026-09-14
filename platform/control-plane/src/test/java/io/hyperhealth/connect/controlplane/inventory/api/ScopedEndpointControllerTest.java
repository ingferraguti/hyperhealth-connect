package io.hyperhealth.connect.controlplane.inventory.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import io.hyperhealth.connect.controlplane.inventory.InventoryId.ApplicationId;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.EndpointId;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.FacilityId;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.OrganizationId;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.TenantId;
import io.hyperhealth.connect.controlplane.inventory.InventoryLifecycleState;
import io.hyperhealth.connect.controlplane.inventory.ScopedEndpoint;
import io.hyperhealth.connect.controlplane.inventory.ScopedEndpointRepository;
import io.hyperhealth.connect.controlplane.inventory.ScopedInventoryService;
import io.hyperhealth.connect.controlplane.inventory.VerifiedFacilityScope;

class ScopedEndpointControllerTest {

    private final TenantId tenantId = new TenantId(UUID.randomUUID());
    private final FacilityId facilityId = new FacilityId(UUID.randomUUID());
    private final EndpointId endpointId = new EndpointId(UUID.randomUUID());
    private final VerifiedFacilityScope scope = new VerifiedFacilityScope(tenantId, facilityId);

    private ScopedEndpointRepository repository;
    private MockMvc mockMvc;

    @BeforeEach
    void createApi() {
        repository = mock(ScopedEndpointRepository.class);
        ScopedInventoryService service = new ScopedInventoryService(repository);
        mockMvc = MockMvcBuilders.standaloneSetup(new ScopedEndpointController(service))
                .setCustomArgumentResolvers(new VerifiedFacilityScopeArgumentResolver())
                .setControllerAdvice(new InventoryProblemAdvice())
                .build();
    }

    @Test
    void ignoresSpoofedScopeHeadersAndFailsBeforePersistence() throws Exception {
        mockMvc.perform(get("/api/v1/endpoints/{endpointId}", endpointId.externalForm())
                        .header("X-Tenant-ID", tenantId.externalForm())
                        .header("X-Facility-ID", facilityId.externalForm()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("HHC-INV-401-001"))
                .andExpect(jsonPath("$.correlationId").isString())
                .andExpect(header().exists("X-Correlation-ID"));

        verifyNoInteractions(repository);
    }

    @Test
    void returnsAResourceOnlyThroughTheVerifiedScope() throws Exception {
        ScopedEndpoint endpoint = new ScopedEndpoint(
                endpointId,
                tenantId,
                new OrganizationId(UUID.randomUUID()),
                facilityId,
                new ApplicationId(UUID.randomUUID()),
                "HHC-SYNTHETIC LIS inbound",
                InventoryLifecycleState.ACTIVE,
                7);
        when(repository.findEndpoint(scope, endpointId)).thenReturn(Optional.of(endpoint));

        mockMvc.perform(get("/api/v1/endpoints/{endpointId}", endpointId.externalForm())
                        .requestAttr(VerifiedFacilityScopeArgumentResolver.REQUEST_ATTRIBUTE, scope))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.endpointId").value(endpointId.externalForm()))
                .andExpect(jsonPath("$.tenantId").value(tenantId.externalForm()))
                .andExpect(jsonPath("$.facilityId").value(facilityId.externalForm()))
                .andExpect(jsonPath("$.displayName").value("HHC-SYNTHETIC LIS inbound"))
                .andExpect(jsonPath("$.rowVersion").value(7));

        verify(repository).findEndpoint(scope, endpointId);
    }

    @Test
    void makesMissingAndCrossScopeResourcesIndistinguishable() throws Exception {
        when(repository.findEndpoint(scope, endpointId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/endpoints/{endpointId}", endpointId.externalForm())
                        .requestAttr(VerifiedFacilityScopeArgumentResolver.REQUEST_ATTRIBUTE, scope))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("HHC-INV-404-001"))
                .andExpect(jsonPath("$.detail")
                        .value("The resource does not exist or is not visible in the current scope."))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(endpointId.externalForm()))));
    }

    @Test
    void rejectsANonCanonicalEndpointIdentifierWithoutCallingPersistence() throws Exception {
        mockMvc.perform(get("/api/v1/endpoints/not-an-endpoint")
                        .requestAttr(VerifiedFacilityScopeArgumentResolver.REQUEST_ATTRIBUTE, scope))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HHC-INV-400-001"));

        verifyNoInteractions(repository);
    }
}
