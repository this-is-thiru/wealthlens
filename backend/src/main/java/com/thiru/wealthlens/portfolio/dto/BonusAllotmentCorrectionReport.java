package com.thiru.wealthlens.portfolio.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * What the bonus allotment-date correction found and, unless a dry run, re-dated (W-088k4b, D-308).
 *
 * @param dryRun whether nothing was written
 * @param auditor who asked, recorded on every document written
 * @param actions one entry per bonus action given, in the order given
 */
public record BonusAllotmentCorrectionReport(boolean dryRun, String auditor, List<ActionCorrection> actions) {

    public BonusAllotmentCorrectionReport {
        actions = List.copyOf(actions);
    }

    /**
     * One action's documents still dated at its ex-date — re-dated, or in a dry run that would be.
     *
     * @param corporateActionId the action
     * @param stockCode its stock
     * @param exDate the date the lots carry now
     * @param allotmentDate the date they are moved to
     * @param assets bonus lots
     * @param transactions bonus transactions
     * @param tradeOutcomes disposals of those lots
     */
    public record ActionCorrection(String corporateActionId, String stockCode, LocalDate exDate,
            LocalDate allotmentDate, long assets, long transactions, long tradeOutcomes) {
    }
}
