package com.thiru.wealthlens.taxengine.rules.definition;

import com.thiru.wealthlens.taxengine.rules.money.Rate;

/**
 * A transferred asset's normal wear and tear: its rate and the method the rate is applied by (W-154f, D-325). A
 * definition carrying its rate, as {@link PremiumShareBands} carries its shares, because a rule version is a cap or a
 * definition, never both.
 *
 * @param rate the wear and tear for each completed year
 * @param method how the rate is applied
 */
public record WearAndTear(Rate rate, WearAndTearMethod method) implements RuleDefinition {

    public WearAndTear {
        if (rate == null || method == null) {
            throw new IllegalArgumentException("Wear and tear needs its rate and its method");
        }
    }
}
