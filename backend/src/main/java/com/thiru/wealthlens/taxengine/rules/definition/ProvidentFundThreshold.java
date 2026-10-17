package com.thiru.wealthlens.taxengine.rules.definition;

import com.thiru.wealthlens.taxengine.rules.money.Money;

/**
 * How much of a person's own contribution to one provident fund in a tax year may go in before the rest is a taxable
 * contribution (S2 Schedule II Sl. 3(a), 4(a), S4 r.277(3)(d); 1961 s.10(11), (12) provisos, Rules 1962 r.9D
 * Explanation (c); D-198).
 *
 * <p>The contribution beyond it enters the fund's taxable contribution account, whose interest is not excluded from
 * total income. The threshold is the higher one where the employer makes no contribution to the fund, and it is
 * tested per fund — "in that fund" — never across a person's funds.
 *
 * @param withoutEmployerContribution the threshold for a fund the person's employer does not contribute to
 * @param otherwise the threshold in other cases
 */
public record ProvidentFundThreshold(Money withoutEmployerContribution, Money otherwise) implements RuleDefinition {

    public ProvidentFundThreshold {
        for (Money threshold : new Money[] {withoutEmployerContribution, otherwise}) {
            if (threshold == null || !threshold.isGreaterThan(Money.ZERO)) {
                throw new IllegalArgumentException(
                        "A provident fund threshold must be above nil, but was " + threshold);
            }
        }
    }

    /** The part of the year's contribution beyond the threshold — "exceeds", so a contribution equal to it is nil. */
    public Money excess(Money contributed, boolean employerContributes) {
        Money threshold = employerContributes ? otherwise : withoutEmployerContribution;
        return contributed.isGreaterThan(threshold) ? contributed.minus(threshold) : Money.ZERO;
    }
}
