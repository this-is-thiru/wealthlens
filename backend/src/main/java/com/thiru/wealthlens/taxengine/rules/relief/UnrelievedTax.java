package com.thiru.wealthlens.taxengine.rules.relief;

import com.thiru.wealthlens.taxengine.rules.money.Money;

/**
 * The tax and surcharge on a given income with relief disabled — the U of surcharge marginal relief.
 *
 * <p>{@code DR 06} G3.2: U is computed by re-entering the tax computation at exactly the threshold,
 * because the slab ladder, the special-rate split and the rebate all bear on it. Only the caller can
 * re-enter the computation, so the caller supplies this (D-073).
 */
@FunctionalInterface
public interface UnrelievedTax {

    /** Tax and surcharge on this income, before any relief. */
    Money at(Money income);
}
