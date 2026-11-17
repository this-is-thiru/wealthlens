package com.thiru.wealthlens.taxplanning.documents.store;

import java.time.Period;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Operational limits on an uploaded document ({@code DR 14} O6; W-131b, D-762 C3a), not tax law, so they live in the profile
 * yamls rather than a rule pack.
 *
 * @param maxBytes O6's "20 MB per file", as bytes
 * @param maxPages O6's "50 pages"
 * @param orphanAfter how old stored bytes no document names must be before they are deleted, so an upload in flight is never taken
 */
@ConfigurationProperties(prefix = "app.tax.documents")
public record DocumentSettings(long maxBytes, int maxPages, Period orphanAfter) {
}
