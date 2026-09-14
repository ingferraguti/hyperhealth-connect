package io.hyperhealth.connect.controlplane.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/** Stable, non-sensitive RFC 9457 responses for authentication and authorization failures. */
final class OidcProblemResponder implements AuthenticationEntryPoint, AccessDeniedHandler {

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authenticationException)
            throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, "HHC-AUTH-401-001", "Authentication required");
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            org.springframework.security.access.AccessDeniedException accessDeniedException)
            throws IOException {
        write(response, HttpStatus.FORBIDDEN, "HHC-AUTH-403-001", "Access denied");
    }

    private static void write(
            HttpServletResponse response, HttpStatus status, String code, String title) throws IOException {
        String correlationId = UUID.randomUUID().toString();
        String instance = "urn:uuid:" + UUID.randomUUID();
        String body = "{\"type\":\"https://docs.hhc.example/problems/access-denied\","
                + "\"title\":\"" + title + "\","
                + "\"status\":" + status.value() + ","
                + "\"detail\":\"The request cannot be authorized.\","
                + "\"instance\":\"" + instance + "\","
                + "\"code\":\"" + code + "\","
                + "\"correlationId\":\"" + correlationId + "\","
                + "\"retryable\":false}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader("X-Correlation-ID", correlationId);
        if (status == HttpStatus.UNAUTHORIZED) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
    }
}
