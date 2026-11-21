package com.thiru.wealthlens.auth.config;

import com.thiru.wealthlens.auth.service.SignInAttempts;
import com.thiru.wealthlens.shared.web.ApiZones;
import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Counts every call to the public auth routes by client before it is served (W-358d2, D-884 C1a), so sign-in, registration and refresh
 * share one limit. The client is the connection's own address (C3a): nothing sits in front of the app to forward another.
 */
@Configuration
@RequiredArgsConstructor
public class SignInLimitInterceptor implements HandlerInterceptor, WebMvcConfigurer {

    private final SignInAttempts attempts;

    @Override
    public boolean preHandle(@Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response, @Nonnull Object handler) {
        attempts.call(request.getRemoteAddr());
        return true;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns(ApiZones.VERSION + "/public/auth/**");
    }
}
