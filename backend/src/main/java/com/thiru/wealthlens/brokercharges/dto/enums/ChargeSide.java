package com.thiru.wealthlens.brokercharges.dto.enums;

/**
 * Which side of a trade a rule applies to.
 *
 * <p>Real rate cards are asymmetric in ways that matter: stamp duty is levied on purchase only,
 * DP charges on sale only, and STT applies to both sides for equity delivery but to the sell side
 * alone for intraday and futures.
 */
public enum ChargeSide {

    /** Purchases only. */
    BUY,

    /** Sales only. */
    SELL,

    /** Both sides of the trade. */
    BOTH
}
