package com.thiru.wealthlens.taxengine.rules.relief;

import com.thiru.wealthlens.taxengine.rules.money.Money;

/**
 * The whole tax up to a maximum, for an income not above a ceiling — "100% of income-tax payable or
 * ₹ X, whichever is less … if such total income does not exceed ₹ Y" (S2 s.156(1), s.156(2)(a)).
 */
public record FlatWithCeiling(Money maximum, Money incomeCeiling) implements ReliefShape {

    public FlatWithCeiling {
        if (maximum == null || incomeCeiling == null) {
            throw new IllegalArgumentException("FlatWithCeiling needs a maximum and an income ceiling");
        }
    }

    @Override
    public ReliefResult apply(ReliefContext ctx) {
        if (ctx.totalIncome().isGreaterThan(incomeCeiling)) {
            return new ReliefResult(Money.ZERO, "total income exceeds " + incomeCeiling);
        }
        Money amount = Money.min(ctx.taxBeforeRelief(), maximum).floorAtZero();
        return new ReliefResult(amount, "the lesser of the tax and " + maximum);
    }
}
