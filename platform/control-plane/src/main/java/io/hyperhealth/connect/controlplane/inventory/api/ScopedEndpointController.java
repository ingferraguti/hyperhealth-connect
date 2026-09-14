package io.hyperhealth.connect.controlplane.inventory.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.hyperhealth.connect.controlplane.inventory.InventoryId.EndpointId;
import io.hyperhealth.connect.controlplane.inventory.ScopedEndpoint;
import io.hyperhealth.connect.controlplane.inventory.ScopedInventoryService;
import io.hyperhealth.connect.controlplane.inventory.VerifiedFacilityScope;

/** Minimal read endpoint proving scope propagation; the complete inventory API belongs to P1-0106. */
@RestController
@ConditionalOnProperty(prefix = "hhc.inventory", name = "enabled", havingValue = "true")
@RequestMapping(path = "/api/v1/endpoints", produces = MediaType.APPLICATION_JSON_VALUE)
public final class ScopedEndpointController {

    private final ScopedInventoryService inventoryService;

    public ScopedEndpointController(ScopedInventoryService inventoryService) {
        this.inventoryService = java.util.Objects.requireNonNull(inventoryService, "inventoryService");
    }

    @GetMapping("/{endpointId}")
    public EndpointResponse getEndpoint(
            VerifiedFacilityScope scope, @PathVariable("endpointId") String externalEndpointId) {
        EndpointId endpointId;
        try {
            endpointId = EndpointId.parse(externalEndpointId);
        } catch (IllegalArgumentException exception) {
            throw new InvalidInventoryIdentifierException(exception);
        }
        return EndpointResponse.from(inventoryService.getEndpoint(scope, endpointId));
    }

    public record EndpointResponse(
            String endpointId,
            String tenantId,
            String organizationId,
            String facilityId,
            String applicationId,
            String displayName,
            String lifecycleState,
            long rowVersion) {

        static EndpointResponse from(ScopedEndpoint endpoint) {
            return new EndpointResponse(
                    endpoint.endpointId().externalForm(),
                    endpoint.tenantId().externalForm(),
                    endpoint.organizationId().externalForm(),
                    endpoint.facilityId().externalForm(),
                    endpoint.applicationId().externalForm(),
                    endpoint.displayName(),
                    endpoint.lifecycleState().name(),
                    endpoint.rowVersion());
        }
    }
}
