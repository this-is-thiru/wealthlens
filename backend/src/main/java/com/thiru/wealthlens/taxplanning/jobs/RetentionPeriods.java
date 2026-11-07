package com.thiru.wealthlens.taxplanning.jobs;

import java.time.Period;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The retention periods kept as configuration (D-449 C3a), both product policy: how long an untouched draft stays active
 * ({@code DR 11} F6) and how long an archived draft that was never acted on is kept ({@code DR 14} O5). How long data held for
 * erasure is kept is data-protection law for every module, {@code app.privacy.erasure-hold} (W-237d C4a); how long a filed draft
 * is kept is tax law, and comes from the rule pack, never from here.
 */
@ConfigurationProperties(prefix = "app.tax.retention")
public record RetentionPeriods(Period archiveAfter, Period purgeArchivedAfter) {
}
