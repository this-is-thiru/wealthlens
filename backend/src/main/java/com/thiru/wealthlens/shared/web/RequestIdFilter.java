package com.thiru.wealthlens.shared.web;

import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Names every request by an id the app makes, returned as {@code X-Request-Id} and printed on every log line written while serving it
 * (W-358d1, D-873). The logs mask emails, so this id — stored with the processing log's record too — is what ties a line to whom it
 * concerned. First of all filters, so a refusal by the security chain carries one as well; never a caller's own id (C2a), so no line's
 * id can be chosen from outside.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";

    /** The MDC key; `logging.pattern.correlation` in application.yaml prints it. */
    static final String LOG_KEY = "requestId";

    /** The id of the request this thread is serving; empty outside one (start-up, scheduled work). */
    public static Optional<String> current() {
        return Optional.ofNullable(MDC.get(LOG_KEY));
    }

    @Override
    protected void doFilterInternal(@Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response,
            @Nonnull FilterChain filterChain) throws ServletException, IOException {
        String id = UUID.randomUUID().toString();
        MDC.put(LOG_KEY, id);
        response.setHeader(HEADER, id);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(LOG_KEY);
        }
    }
}
