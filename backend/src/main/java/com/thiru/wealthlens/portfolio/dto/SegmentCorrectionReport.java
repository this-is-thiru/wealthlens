package com.thiru.wealthlens.portfolio.dto;

import java.util.List;

/**
 * What the segment correction found and, unless a dry run, restored (W-297, D-599).
 *
 * <p>A user listed with a transaction corrected needs §2.12 (their lots) and then §2.11 (their P&amp;L); one listed with
 * only rows corrected needs §2.11. A row in {@code rowsWithMixedBuys} was bought the same day in more than one
 * segment, so its buy side cannot be told from stored data and is left as stored.
 *
 * @param dryRun whether nothing was written
 * @param auditor who asked, recorded on every document written
 * @param transactionsCorrected transactions whose segment was, or in a dry run would be, taken from their charge row
 * @param rowsCorrected outcome rows with at least one segment restored
 * @param users per user, in email order; only users with something corrected
 * @param rowsWithMixedBuys ids of rows whose buys that day disagree on segment
 */
public record SegmentCorrectionReport(boolean dryRun, String auditor, long transactionsCorrected, long rowsCorrected,
        List<UserSegmentCorrection> users, List<String> rowsWithMixedBuys) {

    public SegmentCorrectionReport {
        users = List.copyOf(users);
        rowsWithMixedBuys = List.copyOf(rowsWithMixedBuys);
    }

    /**
     * One user's corrections.
     *
     * @param email the user
     * @param transactions their transactions corrected
     * @param rows their outcome rows corrected
     */
    public record UserSegmentCorrection(String email, long transactions, long rows) {
    }
}
