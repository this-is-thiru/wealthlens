package com.thiru.wealthlens.portfolio.dto;

import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.TradeSegment;
import java.time.LocalDateTime;
import java.util.List;

/**
 * What the backfill of the V2 sales' profit, brokerage and misc found and, unless a dry run, added (W-172, D-373).
 *
 * @param dryRun whether nothing was written
 * @param auditor who asked, recorded on every document changed
 * @param recordedBefore the cut-off: rows written at or after it are recorded in full already (W-169)
 * @param placed the rows whose figures went into the split — added, or in a dry run that would be
 * @param unplaced the rows left out, each with why; brought back to the owner (D-372 C4a)
 * @param alreadyCounted rows an earlier run added, skipped
 * @param writtenAfter recorder rows written at or after the cut-off, skipped
 */
public record V2BackfillReport(boolean dryRun, String auditor, LocalDateTime recordedBefore, List<PlacedRow> placed,
        List<UnplacedRow> unplaced, int alreadyCounted, int writtenAfter) {

    public V2BackfillReport {
        placed = List.copyOf(placed);
        unplaced = List.copyOf(unplaced);
    }

    /** One trade-outcome row, and the split entry its figures went to. */
    public record PlacedRow(String rowId, String email, String financialYear, String side, AssetType assetType,
            TradeSegment segment) {
    }

    /** One trade-outcome row the P&L has nowhere to take. */
    public record UnplacedRow(String rowId, String email, String reason) {
    }
}
