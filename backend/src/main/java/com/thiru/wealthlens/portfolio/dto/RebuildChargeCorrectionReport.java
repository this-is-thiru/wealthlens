package com.thiru.wealthlens.portfolio.dto;

import java.util.List;

/**
 * What a correction of the rebuild's double-counted buy charges found and, unless a dry run, wrote (W-088u7).
 *
 * @param dryRun whether nothing was written
 * @param auditor who asked, recorded on every row written
 * @param rowsExamined rows on rebuild lots consumed by more than one sell, over the users paired
 * @param rowsCorrected of those, the rows whose figures differ from the rebuild's, written unless a dry run
 * @param buyChargesBefore the examined rows' stored buy broker and misc charges together, to the paisa
 * @param buyChargesAfter the same rows' charges as the rebuild now allocates them, to the paisa
 * @param skipped users whose stored rows no longer pair with a rebuild from their transactions; nothing is written
 *     for them
 */
public record RebuildChargeCorrectionReport(boolean dryRun, String auditor, int rowsExamined, int rowsCorrected,
        double buyChargesBefore, double buyChargesAfter, List<SkippedUser> skipped) {

    public RebuildChargeCorrectionReport {
        skipped = List.copyOf(skipped);
    }

    /** A user left as stored, and why. */
    public record SkippedUser(String email, String reason) {
    }
}
