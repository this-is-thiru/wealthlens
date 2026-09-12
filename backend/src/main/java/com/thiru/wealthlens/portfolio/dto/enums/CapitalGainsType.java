package com.thiru.wealthlens.portfolio.dto.enums;

public enum CapitalGainsType {

    SHORT_TERM,

    LONG_TERM,

    /**
     * Not a capital gain at all — a fixed deposit's interest, or an exempt redemption. Distinct from
     * a zero gain: it belongs under a different head of income, so a capital-gains statement must
     * leave it out rather than include it as nil.
     */
    NOT_CAPITAL_GAINS,

    /**
     * The rule could not be determined, most often because a mutual fund's category is not on file.
     * Equity-oriented would be twelve months, a specified fund short-term regardless, and anything
     * else twenty-four — three answers with different tax, so guessing is worse than saying so.
     *
     * <p>Recorded rather than defaulted, for the reason {@code ChargeResolution} records why a
     * charge came out at zero: an unexplained figure is indistinguishable from a wrong one.
     */
    UNCLASSIFIED
}
