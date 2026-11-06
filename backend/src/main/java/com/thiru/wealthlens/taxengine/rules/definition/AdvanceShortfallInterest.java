package com.thiru.wealthlens.taxengine.rules.definition;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;

/**
 * Interest where advance tax paid falls below a share of the assessed tax (2025 s.424(1); 1961 s.234B(1); W-112), by the
 * month or part of a month, on the whole shortfall.
 *
 * @param paidBelowShare the share of the assessed tax below which the interest runs — "less than 90%", so the share itself
 *     does not
 * @param perMonth the interest for each month or part of a month
 */
public record AdvanceShortfallInterest(Rate paidBelowShare, Rate perMonth) implements RuleDefinition {

    public AdvanceShortfallInterest {
        if (paidBelowShare == null || perMonth == null || paidBelowShare.compareTo(Rate.ZERO) <= 0
                || perMonth.compareTo(Rate.ZERO) <= 0) {
            throw new IllegalArgumentException("Advance-tax shortfall interest needs a share and a rate above nil, but was "
                    + paidBelowShare + " and " + perMonth);
        }
    }

    /** Whether {@code paid} falls below the share of {@code assessed}, so the interest runs. */
    public boolean runsOn(Money paid, Money assessed) {
        return paid.isLessThan(paidBelowShare.of(assessed));
    }
}
