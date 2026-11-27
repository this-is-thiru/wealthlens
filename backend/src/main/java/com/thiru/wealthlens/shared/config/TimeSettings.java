package com.thiru.wealthlens.shared.config;

import java.time.ZoneId;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The business time zone (D-958): the zone "today" is a date in, and the one a front end shows times in. Stored times are
 * always UTC and the JVM runs in UTC, so this is the only place a zone is chosen; a missing or unknown zone stops the start.
 *
 * @param zone {@code app.time.zone}, e.g. {@code Asia/Kolkata}
 */
@ConfigurationProperties(prefix = "app.time")
public record TimeSettings(ZoneId zone) {

    public TimeSettings {
        Objects.requireNonNull(zone, "app.time.zone is required: the business time zone, e.g. Asia/Kolkata");
    }
}
