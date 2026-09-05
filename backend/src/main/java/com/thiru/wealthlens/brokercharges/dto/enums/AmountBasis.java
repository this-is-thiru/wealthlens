package com.thiru.wealthlens.brokercharges.dto.enums;

/**
 * Which monetary amount a percentage-based rule applies to.
 *
 * <p>A charge is a percentage <em>of something</em>, and in derivatives that something is not one
 * number: options brokerage and STT are levied on premium, futures charges on notional value, and
 * STT on an exercised option on its intrinsic value. Carrying a single "turnover" figure would make
 * those charges inexpressible without a later schema change.
 *
 * <p>Every rule seeded today uses {@link #TURNOVER}. The rest exist so that adding derivatives, or
 * any instrument priced on a different base, remains a data change rather than a model change.
 */
public enum AmountBasis {

    /** Price multiplied by quantity. The cash-segment default. */
    TURNOVER,

    /** Contract value: price multiplied by lot size and lot count. */
    NOTIONAL,

    /** Option premium multiplied by quantity. */
    PREMIUM,

    /** Settlement price less strike, multiplied by quantity, for an exercised option. */
    INTRINSIC,

    /** The amount invested or remitted, as distinct from a traded value. */
    PRINCIPAL
}
