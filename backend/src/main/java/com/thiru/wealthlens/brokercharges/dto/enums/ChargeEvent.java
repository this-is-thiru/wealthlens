package com.thiru.wealthlens.brokercharges.dto.enums;

/**
 * The occasion that triggers a charge computation.
 *
 * <p>Deliberately distinct from {@link ChargeSide}. A rule declares which <em>sides</em> of a trade
 * it applies to; an event says what actually happened. Conflating the two — as the superseded
 * {@code BrokerChargeTransactionType} did by mixing BUY/SELL with AMC_CHARGES — forces duplicated
 * branches for occasions that are not trades at all.
 */
public enum ChargeEvent {

    /** Securities purchased. */
    BUY,

    /** Securities sold. */
    SELL,

    /** A demat account was opened. */
    ACCOUNT_OPENING,

    /** An annual maintenance billing cycle fell due. */
    AMC_CYCLE,

    /** Order placed through the broker's dealing desk rather than the platform. */
    CALL_AND_TRADE,

    /** An open intraday position was squared off by the broker at cut-off. */
    AUTO_SQUARE_OFF,

    /** Securities pledged or unpledged for margin. */
    PLEDGE
}
