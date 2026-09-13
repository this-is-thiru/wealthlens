package com.thiru.wealthlens.portfolio.dto.enums;

/**
 * The market segment a trade belongs to.
 *
 * <p>Rate cards differ sharply by segment even for the same instrument: equity delivery attracts
 * STT on both sides at 0.1% and a depository charge on sale, while intraday attracts STT on the
 * sell side only at a quarter of the rate and no depository charge at all. Without this dimension
 * those cannot both be expressed.
 *
 * <p>Owned by {@code brokercharges} through Phase A so that no portfolio type changed, and promoted
 * here in Chunk 10b now that a transaction records its own segment. It lives in {@code portfolio}
 * rather than being referenced across the boundary because it is an attribute of a trade, and
 * because {@code AssetEntity} persists it — leaving it in {@code brokercharges} would drag
 * portfolio's stored documents along when that module is renamed to {@code charges}.
 *
 * <p>Both persisted uses store the enum's <em>name</em>, so the move needs no data migration.
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
