package com.thiru.wealthlens.auth.config;


import java.util.Optional;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Who wrote a row, for Spring Data auditing: the actor of a {@link #runAs} block, else the signed-in caller, else {@code
 * "unknown"}. A scheduled job has no security context, so without {@link #runAs} its writes could not be told apart from any
 * other write with no known author (W-257).
 */
@Component
public class SecurityAuditorAware implements AuditorAware<String> {

    private static final ThreadLocal<String> ACTOR = new ThreadLocal<>();

    @Override
    @NonNull
    public Optional<String> getCurrentAuditor() {
        String actor = ACTOR.get();
        if (actor != null) {
            return Optional.of(actor);
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return Optional.of(authentication.getName());
        }

        return Optional.of("unknown");
    }

    /**
     * Runs {@code work} with {@code actor} as the author of every row it writes on this thread. The actor is cleared when the work
     * ends, normally or not, so a pooled thread never carries it into a later write; a failure inside the work reaches the caller.
     */
    public void runAs(String actor, Runnable work) {
        ACTOR.set(actor);
        try {
            work.run();
        } finally {
            ACTOR.remove();
        }
    }
}
