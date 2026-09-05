package com.thiru.wealthlens.brokercharges.dto.enums;

/**
 * The market segment a trade belongs to.
 *
 * <p>Rate cards differ sharply by segment even for the same instrument: equity delivery attracts
 * STT on both sides at 0.1% and a depository charge on sale, while intraday attracts STT on the
 * sell side only at a quarter of the rate and no depository charge at all. Without this dimension
 * those cannot both be expressed.
 *
 * <p>Owned by the charges module in Phase A so that no portfolio type changes. It is promoted to
 * {@code portfolio.dto.enums} at cutover, when transactions begin recording their own segment.
 */
public enum TradeSegment {

    /** Equity taken into, or delivered from, the demat account. */
    DELIVERY,

    /** Equity bought and sold within the same session. */
    INTRADAY,

    /** Futures contracts. */
    FUTURES,

    /** Options contracts. */
    OPTIONS,

    /** Instruments with no segment distinction: mutual funds, bonds, sovereign gold bonds. */
    NA
}
