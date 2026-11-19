package com.thiru.wealthlens.portfolio.dto;

/**
 * What the removal of the deductible charges once stored on {@code trade_outcomes} found and, unless a dry run, removed
 * (W-162b, D-377).
 *
 * @param dryRun whether nothing was written
 * @param auditor who asked, recorded on every row changed
 * @param rows the rows still carrying {@code deductible_buy_charges} or {@code deductible_sell_charges} — changed, or in
 *     a dry run that would be
 */
public record DeductibleChargeRemovalReport(boolean dryRun, String auditor, long rows) {
}
