package com.thiru.wealthlens.portfolio.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How long a trade with no client-supplied key stays de-duplicable.
 *
 * <p>The window is the whole design. A client retrying a timed-out request comes back within
 * seconds; a person deliberately entering the same trade twice takes longer. Sixty seconds covers a
 * 30-second HTTP timeout plus a retry with room to spare, and is short enough that a deliberate
 * re-entry falls outside it.
 *
 * <p><b>The residual risk, stated:</b> a bulk upload submitting two genuinely identical rows
 * milliseconds apart has them collapsed into one. The fix for that is for the uploader to supply
 * explicit keys, which take precedence and carry no window — not to shorten this, because any
 * window short enough to admit those is too short to catch a retry.
 *
 * @param windowSeconds set to 0 to disable the derived-fingerprint fallback entirely; a
 *                      client-supplied key still works
 */
@ConfigurationProperties(prefix = "app.portfolio.idempotency")
public record IdempotencyProperties(Integer windowSeconds) {

    public IdempotencyProperties {
        windowSeconds = windowSeconds == null ? 60 : windowSeconds;
    }

    public boolean fingerprintFallbackEnabled() {
        return windowSeconds > 0;
    }

    public Duration window() {
        return Duration.ofSeconds(windowSeconds);
    }
}
