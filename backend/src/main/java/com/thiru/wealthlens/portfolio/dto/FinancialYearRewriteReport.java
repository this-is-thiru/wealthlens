package com.thiru.wealthlens.portfolio.dto;

/**
 * What the W-086a financial-year rewrite found and, unless a dry run, rewrote (W-088u8c, D-285).
 *
 * @param dryRun whether nothing was written
 * @param auditor who asked, recorded on every row rewritten
 * @param rows the {@code trade_outcomes} rows carrying the {@code FY2025-26} form — rewritten, or in a dry run that
 *     would be
 */
public record FinancialYearRewriteReport(boolean dryRun, String auditor, int rows) {
}
