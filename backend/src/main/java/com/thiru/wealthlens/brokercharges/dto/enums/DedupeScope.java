package com.thiru.wealthlens.brokercharges.dto.enums;

/**
 * The window within which a {@link ChargeBasis#SCOPED_FLAT} charge is levied at most once.
 *
 * <p>Note that this suppresses a <em>repeat</em> charge; it does not cap a sum. A rate card that
 * caps total daily brokerage across all trades needs aggregate post-processing, which the engine
 * does not currently provide.
 */
public enum DedupeScope {

    /** Charged on every occurrence. */
    NONE,

    /** Charged once per stock per calendar day — how depository charges actually work. */
    PER_SCRIP_PER_DAY,

    /** Charged once per order, however many trades fill it. */
    PER_ORDER,

    /** Charged once per calendar day across all activity. */
    PER_DAY
}
