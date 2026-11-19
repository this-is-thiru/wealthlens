package com.thiru.wealthlens.portfolio.dto;

import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.TradeSegment;
import java.util.List;

/**
 * What the move of the P&L's stored short / long pair into its split found and, unless a dry run, did (W-160d3,
 * D-369).
 *
 * @param dryRun whether nothing was written
 * @param auditor who asked, recorded on every document changed
 * @param placed the sides whose pair went into the split — moved, or in a dry run that would be
 * @param unplaced the sides left as they are, each with why; brought back to the owner (D-366 C2a)
 */
public record SplitReshapeReport(boolean dryRun, String auditor, List<PlacedSide> placed,
        List<UnplacedSide> unplaced) {

    public SplitReshapeReport {
        placed = List.copyOf(placed);
        unplaced = List.copyOf(unplaced);
    }

    /** One side of one year's document, and where its pair went. */
    public record PlacedSide(String email, String financialYear, String side, AssetType assetType,
            TradeSegment segment) {
    }

    /** One side of one year's document that its trade outcomes do not place under a single asset type and segment. */
    public record UnplacedSide(String email, String financialYear, String side, String reason) {
    }
}
