package com.thiru.wealthlens.auth.config;

import io.micrometer.common.lang.NonNull;
import java.util.Optional;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityAuditorAware implements AuditorAware<String> {

    // Thread-local storage for system/batch auditors
    private static final ThreadLocal<String> SYSTEM_AUDITOR = new ThreadLocal<>();

    @Override
    @NonNull
    public Optional<String> getCurrentAuditor() {

        // Priority 1: Check if system/batch auditor is set programmatically
        String systemAuditor = SYSTEM_AUDITOR.get();
        if (systemAuditor != null) {
            return Optional.of(systemAuditor);
        }

        // Priority 2: Check Spring Security's authenticated user
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return Optional.of(authentication.getName());
        }

        return Optional.of("unknown");
    }

    public String getAuditor() {
        return getCurrentAuditor().orElse(null);
    }

    // Helper method to set auditor for system/batch processes
    public static void setSystemAuditor(String auditor) {
        SYSTEM_AUDITOR.set(auditor);
    }

    // Clear after use
    public static void clearSystemAuditor() {
        SYSTEM_AUDITOR.remove();
    }
}
