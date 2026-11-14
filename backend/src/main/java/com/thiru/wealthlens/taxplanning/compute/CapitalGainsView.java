package com.thiru.wealthlens.taxplanning.compute;

import com.thiru.wealthlens.taxengine.api.ComputedTax;
import java.time.LocalDate;
import java.util.List;

/**
 * A holder's capital gains as the year's rows read them: the view {@code portfolio} stopped holding when the term and deductible
 * charges left it (W-160g; D-362, D-377).
 *
 * @param realised what was sold in the year, with each charge's limb
 * @param heldAsOf the date the held lots are read as if sold on; null where none was asked for
 * @param held each held lot's term if it were sold then. A what-if, not a sale
 */
public record CapitalGainsView(List<ComputedTax.Bucket> realised, LocalDate heldAsOf, List<HeldLot> held) {

    /**
     * One held lot.
     *
     * @param stockCode what it is
     * @param quantity how much
     * @param acquiredOn since when
     * @param term the term if sold on {@code heldAsOf}; null where {@code refusal} says why not
     * @param termRow the row that decided it
     * @param monthsRead that row's holding period
     * @param refusal why the rows give no term, as a sentence
     */
    public record HeldLot(String stockCode, double quantity, LocalDate acquiredOn, String term, String termRow, Integer monthsRead,
            String refusal) {
    }
}
