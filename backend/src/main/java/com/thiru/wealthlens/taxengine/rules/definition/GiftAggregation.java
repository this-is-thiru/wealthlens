package com.thiru.wealthlens.taxengine.rules.definition;

/**
 * Whether a limb of the charge on gifts tests the year's receipts together or each receipt on its own (S2
 * s.92(2)(m): "the total of which", "the aggregate fair market value"; 1961 s.56(2)(x); D-352). It is the limb's, so a
 * law that totals another kind of receipt is new data.
 */
public enum GiftAggregation {

    /** The year's receipts the limb reaches, tested as one total. */
    YEAR_TOTAL,

    /** Each receipt the limb reaches, tested on its own. */
    EACH_RECEIPT
}
