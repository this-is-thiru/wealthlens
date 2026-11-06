package com.thiru.wealthlens.taxplanning.jobs;

import java.time.Period;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The two periods that are product policy rather than law (D-449 C3a): how long an untouched draft stays active
 * ({@code DR 11} F6) and how long an archived draft that was never acted on is kept ({@code DR 14} O5). How long a filed draft is
 * kept is law, and comes from the rule pack, never from here.
 */
@ConfigurationProperties(prefix = "app.tax.retention")
public record RetentionPeriods(Period archiveAfter, Period purgeArchivedAfter) {
}
