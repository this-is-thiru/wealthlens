package com.thiru.wealthlens.portfolio.dto.enums;

/**
 * How a realised disposal is taxed.
 *
 * <p>Five values, and they are <b>not peers</b>. {@link #SHORT_TERM}, {@link #LONG_TERM} and
 * {@link #SPECULATIVE} are income buckets a return actually carries; {@link #UNCLASSIFIED} is a
 * data-quality state and {@link #NOT_CAPITAL_GAINS} belongs to a different head of income
 * altogether. They share one map on {@code RealisedProfits} so the partition is complete and the
 * amounts reconcile against total proceeds — which is exactly why summing that map is wrong, and
 * why {@link #countsAsCapitalGains()} exists.
 */
public enum CapitalGainsType {

    SHORT_TERM,

    LONG_TERM,

    /**
     * Intraday equity. Speculative business income taxed at slab rate, not capital gains at all
     * (decision D1) — so it is recorded and itemised, but never folded into a capital-gains total.
     */
    SPECULATIVE,

    NOT_CAPITAL_GAINS,

    UNCLASSIFIED;

    /**
     * Whether an amount classified this way belongs in a capital-gains total.
     *
     * <p>Deliberately an exhaustive switch with no {@code default}: a sixth classification must be
     * a decision someone makes here, not something that inherits an answer by falling through.
     */
    public boolean countsAsCapitalGains() {
        return switch (this) {
            case SHORT_TERM, LONG_TERM -> true;
            case SPECULATIVE, NOT_CAPITAL_GAINS, UNCLASSIFIED -> false;
        };
    }
}
