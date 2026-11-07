package com.thiru.wealthlens.taxplanning.jobs;

import java.time.Period;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The retention periods kept as configuration (D-449 C3a; W-235 C2a). Two are product policy: how long an untouched draft stays
 * active ({@code DR 11} F6) and how long an archived draft that was never acted on is kept ({@code DR 14} O5). One is data-protection
 * law, outside the tax rule packs: how long a profile held for erasure is kept before it is erased — the Digital Personal Data
 * Protection Rules, 2025 r.8(3) keep the data and logs of any processing at least one year from it, and the request is the last
 * processing ({@code sources/2026-11-07-data-protection.md}). How long a filed draft is kept is tax law, and comes from the rule
 * pack, never from here.
 */
@ConfigurationProperties(prefix = "app.tax.retention")
public record RetentionPeriods(Period archiveAfter, Period purgeArchivedAfter, Period erasureHold) {
}
