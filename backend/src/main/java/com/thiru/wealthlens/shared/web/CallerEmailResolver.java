package com.thiru.wealthlens.shared.web;

import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Resolves {@link CallerEmail} to the authenticated name; a request without one never reaches a {@link MeApi} route, so none is refused quietly. */
class CallerEmailResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CallerEmail.class) && String.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container, NativeWebRequest request,
            WebDataBinderFactory binderFactory) {
        Authentication caller = SecurityContextHolder.getContext().getAuthentication();
        if (caller == null || caller instanceof AnonymousAuthenticationToken || !caller.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("This route needs a signed-in caller");
        }
        return caller.getName();
    }
}
