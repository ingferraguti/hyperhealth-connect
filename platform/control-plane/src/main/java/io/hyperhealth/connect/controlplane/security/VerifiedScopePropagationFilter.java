package io.hyperhealth.connect.controlplane.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import io.hyperhealth.connect.controlplane.inventory.api.VerifiedFacilityScopeArgumentResolver;

/** Replaces any pre-existing scope attribute with scope from the authenticated bearer token. */
final class VerifiedScopePropagationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        request.removeAttribute(VerifiedFacilityScopeArgumentResolver.REQUEST_ATTRIBUTE);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof HhcJwtAuthenticationToken token && token.isAuthenticated()) {
            request.setAttribute(
                    VerifiedFacilityScopeArgumentResolver.REQUEST_ATTRIBUTE,
                    token.identity().facilityScope());
        }
        filterChain.doFilter(request, response);
    }
}
