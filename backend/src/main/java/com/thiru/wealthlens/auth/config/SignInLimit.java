package com.thiru.wealthlens.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How much guessing the public auth routes allow (W-358d2, D-884 C1a): {@code failuresPerAddress} failed sign-ins for one address from
 * one client, and {@code callsPerClient} calls to {@code /api/v1/public/auth/**} from one client, each within {@code window}. Design
 * values the owner set, not a standard's; settings, so the integration profile can raise the per-client one for its shared client.
 */
@ConfigurationProperties(prefix = "app.auth.sign-in-limit")
public record SignInLimit(int failuresPerAddress, int callsPerClient, Duration window) {

    public SignInLimit {
        if (failuresPerAddress < 1 || callsPerClient < 1 || window == null || window.isNegative() || window.isZero()) {
            throw new IllegalStateException("app.auth.sign-in-limit needs positive failures-per-address, calls-per-client and window");
        }
    }
}
