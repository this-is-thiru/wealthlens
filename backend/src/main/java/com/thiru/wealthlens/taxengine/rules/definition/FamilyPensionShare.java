package com.thiru.wealthlens.taxengine.rules.definition;

import com.thiru.wealthlens.taxengine.rules.money.Money;

/**
 * The deduction against a family pension: a share of it, within a limit, whichever is less (S2 s.93(1)(d); 1961
 * s.57(iia); D-196).
 *
 * <p>Not a cap shape: the Acts state the share as a fraction — "one-third", "thirty-three and one-third per cent" —
 * which a percentage cannot hold exactly, and a rule version is a cap or a definition, never both. So the share is
 * carried as the number it divides by, as {@link CommutedPensionShare} does, and the division happens once, in
 * {@link Money#apportioned}.
 *
 * @param oneOver the divisor — three for "one-third"
 * @param limit the most deducted in the tax year
 */
public record FamilyPensionShare(int oneOver, Money limit) implements RuleDefinition {

    public FamilyPensionShare {
        if (oneOver < 2) {
            throw new IllegalArgumentException("A share of the family pension is a fraction, so its divisor is at"
                    + " least two, but was " + oneOver);
        }
        if (limit == null || !limit.isGreaterThan(Money.ZERO)) {
            throw new IllegalArgumentException("A family pension share needs a limit above nil, but was " + limit);
        }
    }

    /** The share of the pension, or the limit, whichever is less. */
    public Money deduction(Money pension) {
        return Money.min(pension.apportioned(1, oneOver), limit);
    }
}
