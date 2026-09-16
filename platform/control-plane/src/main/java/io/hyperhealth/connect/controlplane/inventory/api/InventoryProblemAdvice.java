package io.hyperhealth.connect.controlplane.inventory.api;

import java.net.URI;
import java.util.UUID;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import io.hyperhealth.connect.controlplane.inventory.InvalidInventoryRequestException;
import io.hyperhealth.connect.controlplane.inventory.InventoryIdempotencyConflictException;
import io.hyperhealth.connect.controlplane.inventory.InventoryLifecycleConflictException;
import io.hyperhealth.connect.controlplane.inventory.InventoryPreconditionFailedException;
import io.hyperhealth.connect.controlplane.inventory.InventoryResourceNotFoundException;

/** RFC 9457 responses that do not reveal whether a resource exists outside the caller scope. */
@RestControllerAdvice(assignableTypes = ScopedEndpointController.class)
@ConditionalOnProperty(prefix = "hhc.inventory", name = "enabled", havingValue = "true")
public final class InventoryProblemAdvice {

    @ExceptionHandler(MissingVerifiedScopeException.class)
    public ProblemDetail missingScope(HttpServletResponse response) {
        return problem(
                response,
                HttpStatus.UNAUTHORIZED,
                "Verified scope required",
                "A verified inventory scope is required.",
                "missing-verified-scope",
                "HHC-INV-401-001");
    }

    @ExceptionHandler(InventoryResourceNotFoundException.class)
    public ProblemDetail resourceNotFound(HttpServletResponse response) {
        return problem(
                response,
                HttpStatus.NOT_FOUND,
                "Inventory resource not found",
                "The resource does not exist or is not visible in the current scope.",
                "inventory-resource-not-found",
                "HHC-INV-404-001");
    }

    @ExceptionHandler(InvalidInventoryIdentifierException.class)
    public ProblemDetail invalidIdentifier(HttpServletResponse response) {
        return problem(
                response,
                HttpStatus.BAD_REQUEST,
                "Invalid inventory identifier",
                "The endpoint identifier is not in canonical form.",
                "invalid-inventory-identifier",
                "HHC-INV-400-001");
    }

    @ExceptionHandler({
        InvalidInventoryRequestException.class,
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class
    })
    public ProblemDetail invalidRequest(HttpServletResponse response) {
        return problem(
                response,
                HttpStatus.BAD_REQUEST,
                "Invalid inventory request",
                "The request does not satisfy the inventory API contract.",
                "invalid-inventory-request",
                "HHC-INV-400-002",
                false);
    }

    @ExceptionHandler(InventoryIdempotencyConflictException.class)
    public ProblemDetail idempotencyCollision(HttpServletResponse response) {
        return problem(
                response,
                HttpStatus.CONFLICT,
                "Idempotency collision",
                "The idempotency key was already used with different request content.",
                "idempotency-collision",
                "HHC-CTRL-IDEMPOTENCY-COLLISION",
                false);
    }

    @ExceptionHandler(MissingInventoryPreconditionException.class)
    public ProblemDetail preconditionRequired(HttpServletResponse response) {
        return problem(
                response,
                HttpStatus.PRECONDITION_REQUIRED,
                "Precondition required",
                "A strong If-Match endpoint ETag is required.",
                "precondition-required",
                "HHC-INV-428-001",
                false);
    }

    @ExceptionHandler(InventoryPreconditionFailedException.class)
    public ProblemDetail staleVersion(HttpServletResponse response) {
        return problem(
                response,
                HttpStatus.PRECONDITION_FAILED,
                "Precondition failed",
                "The resource changed after it was read.",
                "precondition-failed",
                "HHC-CTRL-STALE-VERSION",
                false);
    }

    @ExceptionHandler(InventoryLifecycleConflictException.class)
    public ProblemDetail lifecycleConflict(HttpServletResponse response) {
        return problem(
                response,
                HttpStatus.CONFLICT,
                "Inventory lifecycle conflict",
                "The requested lifecycle transition is not allowed.",
                "inventory-lifecycle-conflict",
                "HHC-INV-409-002",
                false);
    }

    @ExceptionHandler(DataAccessException.class)
    public ProblemDetail persistenceUnavailable(HttpServletResponse response) {
        return problem(
                response,
                HttpStatus.SERVICE_UNAVAILABLE,
                "Inventory service unavailable",
                "The inventory operation could not be completed safely.",
                "inventory-service-unavailable",
                "HHC-INV-503-001",
                true);
    }

    private static ProblemDetail problem(
            HttpServletResponse response,
            HttpStatus status,
            String title,
            String detail,
            String type,
            String code) {
        return problem(response, status, title, detail, type, code, false);
    }

    private static ProblemDetail problem(
            HttpServletResponse response,
            HttpStatus status,
            String title,
            String detail,
            String type,
            String code,
            boolean retryable) {
        String correlationId = UUID.randomUUID().toString();
        response.setHeader("X-Correlation-ID", correlationId);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://docs.hhc.example/problems/" + type));
        problem.setInstance(URI.create("urn:uuid:" + UUID.randomUUID()));
        problem.setProperty("code", code);
        problem.setProperty("correlationId", correlationId);
        problem.setProperty("retryable", retryable);
        return problem;
    }
}
