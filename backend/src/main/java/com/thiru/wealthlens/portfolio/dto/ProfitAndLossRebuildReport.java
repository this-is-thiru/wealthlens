package com.thiru.wealthlens.portfolio.dto;

import java.util.List;

/**
 * What a rebuild of one user's {@code profit_and_loss} from stored rows found and, unless a dry run, wrote (W-283,
 * D-575). Stored and rebuilt totals sit side by side per financial year, so a run that would move a figure shows it
 * before it is written.
 *
 * @param dryRun whether nothing was written
 * @param email the user rebuilt
 * @param salesFolded {@code trade_outcomes} rows folded
 * @param chargesFolded {@code user_charges} rows for a buy or a sale folded
 * @param years every financial year either stored or rebuilt, in order
 */
public record ProfitAndLossRebuildReport(boolean dryRun, String email, int salesFolded, int chargesFolded,
        List<YearComparison> years) {

    public ProfitAndLossRebuildReport {
        years = List.copyOf(years);
    }

    /**
     * One financial year, as stored and as rebuilt.
     *
     * @param hasStoredDocument whether a document was stored for the year; its totals are zero when not
     * @param hasRebuiltDocument whether the rebuild produced one; its totals are zero when not
     */
    public record YearComparison(String financialYear, boolean hasStoredDocument, YearTotals stored,
            boolean hasRebuiltDocument, YearTotals rebuilt) {
    }

    /**
     * A year's figures over both account splits and every asset type and segment, in rupees.
     *
     * @param chargesRupees the charge summary's total, which is kept apart from {@code brokerageRupees}: that is the
     *     gains side's own figure
     */
    public record YearTotals(double purchaseAmountRupees, double sellAmountRupees, double profitRupees,
            double brokerageRupees, double miscChargesRupees, double chargesRupees) {

        public static final YearTotals NONE = new YearTotals(0, 0, 0, 0, 0, 0);
    }
}
