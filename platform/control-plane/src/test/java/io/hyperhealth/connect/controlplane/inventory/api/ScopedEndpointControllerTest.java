package io.hyperhealth.connect.controlplane.inventory.api;

import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.time.Duration;
import java.util.List;
import java.util.Set;
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
import io.hyperhealth.connect.controlplane.inventory.EndpointCreationResult;
import io.hyperhealth.connect.controlplane.inventory.EndpointPageSlice;
import io.hyperhealth.connect.controlplane.inventory.InventoryPreconditionFailedException;
import io.hyperhealth.connect.controlplane.inventory.ScopedEndpoint;
import io.hyperhealth.connect.controlplane.inventory.ScopedEndpointRepository;
import io.hyperhealth.connect.controlplane.inventory.ScopedInventoryService;
import io.hyperhealth.connect.controlplane.inventory.VerifiedFacilityScope;
import io.hyperhealth.connect.controlplane.security.AuthenticatedIdentity;
import io.hyperhealth.connect.controlplane.security.ControlPlaneRole;
import io.hyperhealth.connect.controlplane.security.HhcJwtAuthenticationToken;
import io.hyperhealth.connect.controlplane.security.PrincipalType;

class ScopedEndpointControllerTest {

    private final TenantId tenantId = new TenantId(UUID.randomUUID());
    private final FacilityId facilityId = new FacilityId(UUID.randomUUID());
    private final EndpointId endpointId = new EndpointId(UUID.randomUUID());
    private final VerifiedFacilityScope scope = new VerifiedFacilityScope(tenantId, facilityId);

    private ScopedEndpointRepository repository;
    private MockMvc mockMvc;
    private HhcJwtAuthenticationToken authentication;

    @BeforeEach
    void createApi() {
        repository = mock(ScopedEndpointRepository.class);
        authentication = mock(HhcJwtAuthenticationToken.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.identity()).thenReturn(new AuthenticatedIdentity(
                "synthetic-subject",
                "hhc-control-plane-ui",
                PrincipalType.HUMAN,
                Set.of(ControlPlaneRole.FACILITY_OPERATOR),
                scope));
        ScopedInventoryService service = new ScopedInventoryService(repository);
        mockMvc = MockMvcBuilders.standaloneSetup(new ScopedEndpointController(
                        service, new InventoryCursorCodec(new byte[32], Duration.ofMinutes(15))))
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
    void rejectsAConfusedDeputyScopeBeforePersistence() throws Exception {
        VerifiedFacilityScope foreignRequestScope = new VerifiedFacilityScope(
                new TenantId(UUID.randomUUID()), new FacilityId(UUID.randomUUID()));

        mockMvc.perform(get("/api/v1/endpoints/{endpointId}", endpointId.externalForm())
                        .principal(authentication)
                        .requestAttr(
                                VerifiedFacilityScopeArgumentResolver.REQUEST_ATTRIBUTE,
                                foreignRequestScope)
                        .header("X-Tenant-ID", tenantId.externalForm())
                        .header("X-Facility-ID", facilityId.externalForm()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("HHC-INV-401-001"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(endpointId.externalForm()))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(foreignRequestScope.tenantId().externalForm()))));

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
        when(repository.findEndpoint(eq(scope), any(), any(), eq(endpointId))).thenReturn(Optional.of(endpoint));

        mockMvc.perform(get("/api/v1/endpoints/{endpointId}", endpointId.externalForm())
                        .principal(authentication)
                        .requestAttr(VerifiedFacilityScopeArgumentResolver.REQUEST_ATTRIBUTE, scope))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-ID"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.endpointId").value(endpointId.externalForm()))
                .andExpect(jsonPath("$.tenantId").value(tenantId.externalForm()))
                .andExpect(jsonPath("$.facilityId").value(facilityId.externalForm()))
                .andExpect(jsonPath("$.displayName").value("HHC-SYNTHETIC LIS inbound"))
                .andExpect(jsonPath("$.rowVersion").value(7));


        verify(repository).findEndpoint(eq(scope), any(), any(), eq(endpointId));
    }

    @Test
    void listsABoundedScopedPageWithoutADataCount() throws Exception {
        ScopedEndpoint endpoint = endpoint(3);
        when(repository.listEndpoints(eq(scope), any(), any(), any()))
                .thenReturn(new EndpointPageSlice(List.of(endpoint), false));

        mockMvc.perform(get("/api/v1/endpoints")
                        .principal(authentication)
                        .requestAttr(VerifiedFacilityScopeArgumentResolver.REQUEST_ATTRIBUTE, scope))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items[0].endpointId").value(endpointId.externalForm()))
                .andExpect(jsonPath("$.page.limit").value(50))
                .andExpect(jsonPath("$.page.hasMore").value(false))
                .andExpect(jsonPath("$.total").doesNotExist());
    }

    @Test
    void createsWithAReplayableIdempotencyContract() throws Exception {
        ScopedEndpoint endpoint = endpoint(0);
        when(repository.createEndpoint(eq(scope), any(), any(), any(), any(), eq("Synthetic endpoint")))
                .thenReturn(new EndpointCreationResult(endpoint, true));

        mockMvc.perform(post("/api/v1/endpoints")
                        .principal(authentication)
                        .requestAttr(VerifiedFacilityScopeArgumentResolver.REQUEST_ATTRIBUTE, scope)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"applicationId":"%s","displayName":"Synthetic endpoint"}
                                """.formatted(endpoint.applicationId().externalForm())))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"rv-0\""))
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(header().string("Location", "/api/v1/endpoints/" + endpointId.externalForm()))
                .andExpect(jsonPath("$.displayName").value("Synthetic endpoint"));
    }

    @Test
    void rejectsMissingMutationGuardsBeforePersistence() throws Exception {
        mockMvc.perform(post("/api/v1/endpoints")
                        .principal(authentication)
                        .requestAttr(VerifiedFacilityScopeArgumentResolver.REQUEST_ATTRIBUTE, scope)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"applicationId":"a-%s","displayName":"Synthetic endpoint"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HHC-INV-400-002"));

        mockMvc.perform(patch("/api/v1/endpoints/{endpointId}", endpointId.externalForm())
                        .principal(authentication)
                        .requestAttr(VerifiedFacilityScopeArgumentResolver.REQUEST_ATTRIBUTE, scope)
                        .contentType("application/merge-patch+json")
                        .content("{\"displayName\":\"Renamed\"}"))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value("HHC-INV-428-001"));

        verifyNoInteractions(repository);
    }

    @Test
    void mapsAStaleEtagToPreconditionFailedWithoutLeakingState() throws Exception {
        when(repository.updateEndpoint(eq(scope), any(), any(), eq(endpointId), eq(2L), any()))
                .thenThrow(new InventoryPreconditionFailedException());

        mockMvc.perform(patch("/api/v1/endpoints/{endpointId}", endpointId.externalForm())
                        .principal(authentication)
                        .requestAttr(VerifiedFacilityScopeArgumentResolver.REQUEST_ATTRIBUTE, scope)
                        .header("If-Match", "\"rv-2\"")
                        .contentType("application/merge-patch+json")
                        .content("{\"lifecycleState\":\"SUSPENDED\"}"))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("HHC-CTRL-STALE-VERSION"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(endpointId.externalForm()))));
    }

    @Test
    void rejectsExplicitNullAndUnknownMergePatchPropertiesAtomically() throws Exception {
        mockMvc.perform(patch("/api/v1/endpoints/{endpointId}", endpointId.externalForm())
                        .principal(authentication)
                        .requestAttr(VerifiedFacilityScopeArgumentResolver.REQUEST_ATTRIBUTE, scope)
                        .header("If-Match", "\"rv-2\"")
                        .contentType("application/merge-patch+json")
                        .content("{\"displayName\":null,\"lifecycleState\":\"SUSPENDED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HHC-INV-400-002"));

        mockMvc.perform(patch("/api/v1/endpoints/{endpointId}", endpointId.externalForm())
                        .principal(authentication)
                        .requestAttr(VerifiedFacilityScopeArgumentResolver.REQUEST_ATTRIBUTE, scope)
                        .header("If-Match", "\"rv-2\"")
                        .contentType("application/merge-patch+json")
                        .content("{\"displayName\":\"Renamed\",\"unexpected\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HHC-INV-400-002"));

        verifyNoInteractions(repository);
    }

    @Test
    void makesMissingAndCrossScopeResourcesIndistinguishable() throws Exception {
        when(repository.findEndpoint(eq(scope), any(), any(), eq(endpointId))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/endpoints/{endpointId}", endpointId.externalForm())
                        .principal(authentication)
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
                        .principal(authentication)
                        .requestAttr(VerifiedFacilityScopeArgumentResolver.REQUEST_ATTRIBUTE, scope))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HHC-INV-400-001"));

        verifyNoInteractions(repository);
    }

    private ScopedEndpoint endpoint(long rowVersion) {
        return new ScopedEndpoint(
                endpointId,
                tenantId,
                new OrganizationId(UUID.randomUUID()),
                facilityId,
                new ApplicationId(UUID.randomUUID()),
                "Synthetic endpoint",
                InventoryLifecycleState.ACTIVE,
                rowVersion);
    }
}
