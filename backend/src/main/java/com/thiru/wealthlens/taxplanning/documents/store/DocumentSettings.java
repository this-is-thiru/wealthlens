package com.thiru.wealthlens.taxplanning.documents.store;

import java.time.Duration;
import java.time.Period;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Operational limits on an uploaded document ({@code DR 14} O6; W-131b, D-762 C3a), not tax law, so they live in the profile
 * yamls rather than a rule pack.
 *
 * @param maxBytes O6's "20 MB per file", as bytes
 * @param maxPages O6's "50 pages"
 * @param orphanAfter how old stored bytes no document names must be before they are deleted, so an upload in flight is never taken
 * @param parseBudget how long one parse may run before it is a failed attempt (O6's wall-clock budget; W-131c, D-764 C3a)
 * @param enabled whether the documents endpoints answer at all: off until a scan and isolation exist (W-131d1, D-765 C2a)
 */
@ConfigurationProperties(prefix = "app.tax.documents")
public record DocumentSettings(long maxBytes, int maxPages, Period orphanAfter, Duration parseBudget, boolean enabled) {
}
