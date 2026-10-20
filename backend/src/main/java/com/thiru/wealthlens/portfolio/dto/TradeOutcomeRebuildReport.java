package com.thiru.wealthlens.portfolio.dto;

import java.util.List;

/**
 * What a rebuild of {@code trade_outcomes} from raw transactions found and, unless a dry run, wrote (W-088u8b, D-284).
 *
 * @param dryRun whether nothing was written
 * @param auditor who asked; the auditing records the same name on every row written
 * @param usersExamined users with transactions
 * @param usersRebuilt of those, the users with no rows, whose rows were (or in a dry run would be) written
 * @param rowsWritten rows written, or in a dry run that would be
 * @param skipped users left as they are because they already have rows, whichever writer made them
 */
public record TradeOutcomeRebuildReport(boolean dryRun, String auditor, int usersExamined, int usersRebuilt,
        int rowsWritten, List<SkippedUser> skipped) {

    public TradeOutcomeRebuildReport {
        skipped = List.copyOf(skipped);
    }

    /** A user who already has rows, and how many. */
    public record SkippedUser(String email, long existingRows) {
    }
}
