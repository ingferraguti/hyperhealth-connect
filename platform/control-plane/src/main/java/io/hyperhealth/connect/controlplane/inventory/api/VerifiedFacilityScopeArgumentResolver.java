package io.hyperhealth.connect.controlplane.inventory.api;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import io.hyperhealth.connect.controlplane.inventory.VerifiedFacilityScope;

/** Resolves only a server-side request attribute; tenant/facility HTTP headers are ignored. */
public final class VerifiedFacilityScopeArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String REQUEST_ATTRIBUTE = VerifiedFacilityScope.class.getName();

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(VerifiedFacilityScope.class);
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer modelAndViewContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        Object scope = webRequest.getAttribute(REQUEST_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (scope instanceof VerifiedFacilityScope verifiedScope) {
            return verifiedScope;
        }
        throw new MissingVerifiedScopeException();
    }
}
