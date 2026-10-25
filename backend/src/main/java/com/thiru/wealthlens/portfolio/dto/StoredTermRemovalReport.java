package com.thiru.wealthlens.portfolio.dto;

/**
 * What the removal of {@code portfolio}'s stored term from {@code trade_outcomes} found and, unless a dry run, removed
 * (W-160c, D-365).
 *
 * @param dryRun whether nothing was written
 * @param auditor who asked, recorded on every row changed
 * @param rows the rows still carrying {@code capital_gains_type} or {@code classification_reason} — changed, or in a
 *     dry run that would be
 */
public record StoredTermRemovalReport(boolean dryRun, String auditor, long rows) {
}
