package com.thiru.wealthlens.taxplanning.compute;

import java.time.LocalDate;
import java.util.List;

/**
 * The lots a holder still holds, each with the term a sale on one date would have by the year's rows, and each stock's
 * quantity by term (W-262): what {@code portfolio}'s one-year split answered before the term left it (D-362, D-370). A
 * what-if on a date, never a sale, so nothing here is a gain or a tax.
 *
 * @param asOf the date the lots are read as if sold on
 * @param lots the lots listed, those of one term where one was asked for
 * @param totals each listed stock's quantity by term, over all of that stock's lots
 */
public record HeldAssetsView(LocalDate asOf, List<Lot> lots, List<StockTotal> totals) {

    public HeldAssetsView {
        lots = List.copyOf(lots);
        totals = List.copyOf(totals);
    }

    /**
     * One held lot.
     *
     * @param stockCode what it is
     * @param broker the broker it is held with; null where none is recorded
     * @param accountHolder whose account holds it; null where none is recorded
     * @param quantity how much is still held
     * @param acquiredOn since when
     * @param term the term if sold on {@code asOf}; null where {@code note} says why the rows give none
     * @param termRow the row that decided it
     * @param monthsRead that row's holding period
     * @param longTermFrom the first day a sale is long-term; null where no term is given, or the year deciding it is not known
     * @param note why there is no term or no date, as a sentence; null where both are given
     */
    public record Lot(String stockCode, String broker, String accountHolder, double quantity, LocalDate acquiredOn, String term,
            String termRow, Integer monthsRead, LocalDate longTermFrom, String note) {
    }

    /**
     * One stock's held quantity by term.
     *
     * @param quantityWithoutTerm what the rows give no term for, so the three add up to all that is held
     */
    public record StockTotal(String stockCode, double longTermQuantity, double shortTermQuantity, double quantityWithoutTerm) {
    }
}
