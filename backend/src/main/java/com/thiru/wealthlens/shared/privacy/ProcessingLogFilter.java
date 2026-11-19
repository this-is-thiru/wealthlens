package com.thiru.wealthlens.shared.privacy;

import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Writes a {@link ProcessingRecord} for every request made by, or on behalf of, a named person (W-237e C1a). It sits in the
 * security chain ahead of the token check, so a request refused there is recorded too, with the result the person was given.
 * A request with no named person — no token, no login attempt — is not recorded: there is nobody whose access it shows. The
 * record is written after the response, so a failed write cannot change what the person was told; it is raised, not dropped.
 */
@Component
@RequiredArgsConstructor
public class ProcessingLogFilter extends OncePerRequestFilter {

    private static final String LOGIN_ATTEMPT = ProcessingLogFilter.class.getName() + ".loginAttempt";

    private static final String SUBJECT_VARIABLE = "email";

    private final MongoTemplate mongoTemplate;
    private final Clock clock;

    /** Names the email a login is attempted for, which no token carries yet; called by the login endpoint before it checks. */
    public static void nameLoginAttempt(String email) {
        RequestContextHolder.currentRequestAttributes().setAttribute(LOGIN_ATTEMPT, email, RequestAttributes.SCOPE_REQUEST);
    }

    @Override
    protected void doFilterInternal(@Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response,
            @Nonnull FilterChain filterChain) throws ServletException, IOException {
        filterChain.doFilter(request, response);
        Optional<String> principal = principal(request);
        if (principal.isPresent()) {
            mongoTemplate.insert(record(principal.get(), request, response));
        }
    }

    private ProcessingRecord record(String principal, HttpServletRequest request, HttpServletResponse response) {
        ProcessingRecord record = new ProcessingRecord();
        record.setId(UUID.randomUUID().toString());
        record.setPrincipal(principal);
        record.setSubjectEmail(subject(request).orElse(principal));
        record.setMethod(request.getMethod());
        route(request).ifPresent(record::setRoute);
        record.setStatus(response.getStatus());
        record.setRemoteAddress(request.getRemoteAddr());
        record.setRecordedAt(LocalDateTime.now(clock.withZone(ZoneOffset.UTC)));
        return record;
    }

    private static Optional<String> principal(HttpServletRequest request) {
        if (request.getAttribute(LOGIN_ATTEMPT) instanceof String attempted) {
            return Optional.of(attempted);
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        return Optional.of(authentication.getName());
    }

    private static Optional<String> subject(HttpServletRequest request) {
        if (request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) instanceof Map<?, ?> variables
                && variables.get(SUBJECT_VARIABLE) instanceof String email) {
            return Optional.of(email);
        }
        return Optional.empty();
    }

    /** The pattern, never the path itself: a path can carry values, and only the route is needed to see the access. */
    private static Optional<String> route(HttpServletRequest request) {
        return Optional.ofNullable(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)).map(Object::toString);
    }
}
