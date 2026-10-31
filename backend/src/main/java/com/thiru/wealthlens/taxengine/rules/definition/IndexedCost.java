package com.thiru.wealthlens.taxengine.rules.definition;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;

/**
 * What a row of {@code RS-CG-INDEXED-COST} says of a capital asset: whether its cost is lifted by the Cost Inflation
 * Index, and from which year at the latest — the later of the first year it was held and the year beginning 1-4-2001
 * (2025 s.72(8)(b); 1961 s.48 Explanation (iii); W-088g, D-426).
 *
 * <p>The floor is the law's, so it is data and a change in it is a new version; the arithmetic itself, and the table it
 * divides by, are {@code RATE-CG-COST-INFLATION-INDEX}'s ({@code IndexByYear}).
 *
 * @param floorYear the year the divisor is never taken earlier than
 */
public record IndexedCost(TaxYearId floorYear) implements RuleDefinition {

    public IndexedCost {
        if (floorYear == null) {
            throw new IllegalArgumentException("An indexed-cost row must name the year indexation starts from at the"
                    + " latest");
        }
    }
}
