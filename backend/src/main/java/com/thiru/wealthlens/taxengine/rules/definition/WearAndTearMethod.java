package com.thiru.wealthlens.taxengine.rules.definition;

/**
 * How a transferred asset's normal wear and tear is worked out from its rate (S4 r.15(5) Table IV Sl. 8; Rules 1962
 * r.3(7)(viii); D-119) — which assets take which is the {@code RS-PERQ-ASSET-TRANSFER} rows' (W-154f).
 */
public enum WearAndTearMethod {

    /** "By reducing balance method": the rate off what is left, for each completed year. */
    REDUCING_BALANCE,

    /** "Of the cost of the asset for each completed year": the rate of the cost, once a year, down to nil. */
    SHARE_OF_COST_EACH_YEAR
}
