package io.hyperhealth.connect.controlplane.inventory.api;

import java.net.URI;
import java.util.UUID;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

    private static ProblemDetail problem(
            HttpServletResponse response,
            HttpStatus status,
            String title,
            String detail,
            String type,
            String code) {
        String correlationId = UUID.randomUUID().toString();
        response.setHeader("X-Correlation-ID", correlationId);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://docs.hhc.example/problems/" + type));
        problem.setInstance(URI.create("urn:uuid:" + UUID.randomUUID()));
        problem.setProperty("code", code);
        problem.setProperty("correlationId", correlationId);
        problem.setProperty("retryable", false);
        return problem;
    }
}
