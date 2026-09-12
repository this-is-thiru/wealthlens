package com.thiru.wealthlens.portfolio.holding;

/**
 * What a holding-period policy concludes.
 *
 * <p>Not a number of months, because two of the three answers are not durations. A specified mutual
 * fund acquired on or after 1 April 2023 is short-term however long it is held, and a fixed deposit
 * is not a capital asset disposal at all. A model whose only output is a threshold has to express
 * those as an impossible number, which is how "always short-term" becomes "long-term after 1200
 * months" and stops being readable.
 */
public enum HoldingPeriodOutcome {

    /** Long-term once held beyond the policy's month threshold. */
    LONG_TERM_AFTER_MONTHS,

    /** Short-term regardless of holding period — s.50AA specified funds, unlisted debentures. */
    ALWAYS_SHORT_TERM,

    /** Not a capital gain: interest income, or an exempt redemption. */
    NOT_CAPITAL_GAINS
}
