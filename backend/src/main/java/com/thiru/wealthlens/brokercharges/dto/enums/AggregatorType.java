package com.thiru.wealthlens.brokercharges.dto.enums;

/**
 * How a percentage-derived amount is reconciled with a fixed amount when a rule declares both.
 *
 * <p>The canonical case is discount brokerage quoted as "0.03% or ₹20, whichever is lower", which
 * is {@link #MIN}. A rule carrying both a rate and a flat amount without an aggregator is rejected
 * at validation rather than silently resolving to zero.
 */
public enum AggregatorType {

    /** Take the lower of the percentage-derived and fixed amounts. */
    MIN,

    /** Take the higher of the percentage-derived and fixed amounts. */
    MAX
}
