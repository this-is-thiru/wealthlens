package com.thiru.wealthlens.taxengine.rules.definition;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;

/**
 * How much may be received without, or for inadequate, consideration before it is income (S2 s.92(2)(m); 1961
 * s.56(2)(x); D-201): one amount every limb is tested against, and for immovable property bought below its stamp duty
 * value a tolerance that is the higher of that amount and a share of the consideration.
 *
 * @param exceeds the amount a total, a value or a shortfall must exceed — "exceeds", so equal is not
 * @param considerationShare the share of the consideration the immovable-property tolerance may rise to
 */
public record GiftThreshold(Money exceeds, Rate considerationShare) implements RuleDefinition {

    public GiftThreshold {
        if (exceeds == null || !exceeds.isGreaterThan(Money.ZERO) || considerationShare == null
                || considerationShare.compareTo(Rate.ZERO) <= 0) {
            throw new IllegalArgumentException("A gift threshold needs an amount and a share of consideration above"
                    + " nil, but was " + exceeds + " and " + considerationShare);
        }
    }

    /** Whether {@code amount} — a year's sums, a value or a shortfall — is above the threshold. */
    public boolean isExceededBy(Money amount) {
        return amount.isGreaterThan(exceeds);
    }

    /**
     * The stamp duty value beyond the consideration, where that excess is more than the higher of the threshold and
     * the share of the consideration (S2 s.92(2)(m)(ii)(B); 1961 s.56(2)(x)(b)(B)); nil otherwise. With no
     * consideration it is the whole stamp duty value above the threshold, as (ii)(A) / (b)(A) says.
     */
    public Money immovablePropertyCharged(Money consideration, Money stampDutyValue) {
        Money excess = stampDutyValue.minus(consideration);
        Money tolerance = Money.max(exceeds, considerationShare.of(consideration));
        return excess.isGreaterThan(tolerance) ? excess : Money.ZERO;
    }

    /**
     * The fair market value beyond the consideration, where it exceeds the threshold (S2 s.92(2)(m)(iii)(B); 1961
     * s.56(2)(x)(c)(B)); nil otherwise.
     */
    public Money propertyShortfallCharged(Money consideration, Money fairMarketValue) {
        Money shortfall = fairMarketValue.minus(consideration);
        return isExceededBy(shortfall) ? shortfall : Money.ZERO;
    }
}
