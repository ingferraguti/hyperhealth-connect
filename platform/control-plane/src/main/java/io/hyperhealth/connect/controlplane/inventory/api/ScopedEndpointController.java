package io.hyperhealth.connect.controlplane.inventory.api;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.hyperhealth.connect.controlplane.inventory.EndpointCreationResult;
import io.hyperhealth.connect.controlplane.inventory.EndpointPageSlice;
import io.hyperhealth.connect.controlplane.inventory.EndpointPatch;
import io.hyperhealth.connect.controlplane.inventory.EndpointQuery;
import io.hyperhealth.connect.controlplane.inventory.InventoryActor;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.ApplicationId;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.EndpointId;
import io.hyperhealth.connect.controlplane.inventory.InventoryLifecycleState;
import io.hyperhealth.connect.controlplane.inventory.InvalidInventoryRequestException;
import io.hyperhealth.connect.controlplane.inventory.ScopedEndpoint;
import io.hyperhealth.connect.controlplane.inventory.ScopedInventoryService;
import io.hyperhealth.connect.controlplane.inventory.VerifiedFacilityScope;
import io.hyperhealth.connect.controlplane.security.HhcJwtAuthenticationToken;

/** Facility-scoped Endpoint API v0.2 with bounded keyset pagination and safe mutations. */
@RestController
@ConditionalOnProperty(prefix = "hhc.inventory", name = "enabled", havingValue = "true")
@RequestMapping(path = "/api/v1/endpoints", produces = MediaType.APPLICATION_JSON_VALUE)
public final class ScopedEndpointController {

    static final String IDEMPOTENCY_REPLAYED_HEADER = "Idempotency-Replayed";
    static final String MERGE_PATCH_MEDIA_TYPE = "application/merge-patch+json";
    private static final int DEFAULT_PAGE_LIMIT = 50;

    private final ScopedInventoryService inventoryService;
    private final InventoryCursorCodec cursorCodec;

    public ScopedEndpointController(
            ScopedInventoryService inventoryService, InventoryCursorCodec cursorCodec) {
        this.inventoryService = Objects.requireNonNull(inventoryService, "inventoryService");
        this.cursorCodec = Objects.requireNonNull(cursorCodec, "cursorCodec");
    }

    @GetMapping
    public ResponseEntity<EndpointCollectionResponse> listEndpoints(
            VerifiedFacilityScope scope,
            HhcJwtAuthenticationToken authentication,
            @RequestParam(name = "applicationId", required = false) String externalApplicationId,
            @RequestParam(name = "lifecycleState", required = false) String lifecycleState,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        InventoryActor actor = actor(authentication, scope);
        EndpointQuery baseQuery = new EndpointQuery(
                optionalApplicationId(externalApplicationId),
                optionalLifecycleState(lifecycleState),
                Optional.empty(),
                limit);
        Optional<EndpointId> after = cursorCodec.decode(cursor, actor, scope, baseQuery);
        EndpointQuery repositoryQuery = new EndpointQuery(
                baseQuery.applicationId(), baseQuery.lifecycleState(), after, baseQuery.limit());
        EndpointPageSlice page = inventoryService.listEndpoints(scope, repositoryQuery);
        String nextCursor = page.hasMore()
                ? cursorCodec.encode(
                        actor,
                        scope,
                        baseQuery,
                        page.items().getLast().endpointId())
                : null;
        EndpointCollectionResponse response = new EndpointCollectionResponse(
                page.items().stream().map(EndpointResponse::from).toList(),
                new PageResponse(nextCursor, limit, page.hasMore()));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    @GetMapping("/{endpointId}")
    public ResponseEntity<EndpointResponse> getEndpoint(
            VerifiedFacilityScope scope, @PathVariable("endpointId") String externalEndpointId) {
        ScopedEndpoint endpoint = inventoryService.getEndpoint(scope, endpointId(externalEndpointId));
        return response(HttpStatus.OK, endpoint, false);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EndpointResponse> createEndpoint(
            VerifiedFacilityScope scope,
            HhcJwtAuthenticationToken authentication,
            @RequestHeader(name = "Idempotency-Key", required = false) String rawIdempotencyKey,
            @RequestBody CreateEndpointRequest request) {
        if (request == null) {
            throw new InvalidInventoryRequestException("A JSON request body is required");
        }
        InventoryActor actor = actor(authentication, scope);
        EndpointCreationResult result = inventoryService.createEndpoint(
                scope,
                actor,
                idempotencyKey(rawIdempotencyKey),
                applicationId(request.applicationId()),
                request.displayName());
        return response(HttpStatus.CREATED, result.endpoint(), result.replayed());
    }

    @PatchMapping(path = "/{endpointId}", consumes = MERGE_PATCH_MEDIA_TYPE)
    public ResponseEntity<EndpointResponse> patchEndpoint(
            VerifiedFacilityScope scope,
            @PathVariable("endpointId") String externalEndpointId,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestBody JsonNode request) {
        if (request == null || !request.isObject()) {
            throw new InvalidInventoryRequestException("A JSON object request body is required");
        }
        request.propertyNames().forEach(field -> {
            if (!field.equals("displayName") && !field.equals("lifecycleState")) {
                throw new InvalidInventoryRequestException("The merge patch contains an unknown property");
            }
        });
        Optional<String> displayName = patchString(request, "displayName");
        Optional<InventoryLifecycleState> state = patchString(request, "lifecycleState")
                .map(ScopedEndpointController::lifecycleState);
        EndpointPatch patch;
        try {
            patch = new EndpointPatch(displayName, state);
        } catch (IllegalArgumentException exception) {
            throw new InvalidInventoryRequestException("At least one mutable property is required", exception);
        }
        ScopedEndpoint endpoint = inventoryService.updateEndpoint(
                scope,
                endpointId(externalEndpointId),
                EndpointEtag.parseRequired(ifMatch),
                patch);
        return response(HttpStatus.OK, endpoint, false);
    }

    private static ResponseEntity<EndpointResponse> response(
            HttpStatus status, ScopedEndpoint endpoint, boolean replayed) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .eTag(EndpointEtag.from(endpoint.rowVersion()));
        if (status == HttpStatus.CREATED) {
            builder.location(URI.create("/api/v1/endpoints/" + endpoint.endpointId().externalForm()));
        }
        if (replayed) {
            builder.header(IDEMPOTENCY_REPLAYED_HEADER, "true");
        }
        return builder.body(EndpointResponse.from(endpoint));
    }

    private static InventoryActor actor(
            HhcJwtAuthenticationToken authentication, VerifiedFacilityScope scope) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !authentication.identity().facilityScope().equals(scope)) {
            throw new MissingVerifiedScopeException();
        }
        return new InventoryActor(
                authentication.identity().subject(), authentication.identity().authorizedParty());
    }

    private static EndpointId endpointId(String value) {
        try {
            return EndpointId.parse(value);
        } catch (IllegalArgumentException exception) {
            throw new InvalidInventoryIdentifierException(exception);
        }
    }

    private static ApplicationId applicationId(String value) {
        if (value == null) {
            throw new InvalidInventoryRequestException("applicationId is required");
        }
        try {
            return ApplicationId.parse(value);
        } catch (IllegalArgumentException exception) {
            throw new InvalidInventoryRequestException("applicationId is not in canonical form", exception);
        }
    }

    private static Optional<ApplicationId> optionalApplicationId(String value) {
        return value == null ? Optional.empty() : Optional.of(applicationId(value));
    }

    private static InventoryLifecycleState lifecycleState(String value) {
        if (value == null || !value.equals(value.toUpperCase(Locale.ROOT))) {
            throw new InvalidInventoryRequestException("lifecycleState is not an allowed canonical value");
        }
        try {
            return InventoryLifecycleState.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new InvalidInventoryRequestException("lifecycleState is not an allowed canonical value", exception);
        }
    }

    private static Optional<InventoryLifecycleState> optionalLifecycleState(String value) {
        return value == null ? Optional.empty() : Optional.of(lifecycleState(value));
    }

    private static Optional<String> patchString(JsonNode request, String property) {
        if (!request.has(property)) {
            return Optional.empty();
        }
        JsonNode value = request.get(property);
        if (value == null || !value.isString()) {
            throw new InvalidInventoryRequestException(
                    property + " must be a non-null JSON string when present");
        }
        return Optional.of(value.stringValue());
    }

    private static UUID idempotencyKey(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidInventoryRequestException("Idempotency-Key is required");
        }
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equals(value) || parsed.version() != 4) {
                throw new IllegalArgumentException("not a canonical UUIDv4");
            }
            return parsed;
        } catch (IllegalArgumentException exception) {
            throw new InvalidInventoryRequestException(
                    "Idempotency-Key must be a canonical random UUIDv4", exception);
        }
    }

    public record CreateEndpointRequest(String applicationId, String displayName) {}

    public record EndpointCollectionResponse(List<EndpointResponse> items, PageResponse page) {
        public EndpointCollectionResponse {
            items = List.copyOf(items);
            Objects.requireNonNull(page, "page");
        }
    }

    public record PageResponse(String nextCursor, int limit, boolean hasMore) {}

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
