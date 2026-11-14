package com.thiru.wealthlens.taxengine.rules.relief;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.reason.Reason;
import com.thiru.wealthlens.taxengine.rules.reason.ReasonCodes;
import com.thiru.wealthlens.taxengine.rules.reason.ReasonFact;
import java.util.Map;

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
            return new ReliefResult(Money.ZERO, "total income exceeds " + incomeCeiling, new Reason(
                    ReasonCodes.RELIEF_INCOME_ABOVE_CEILING, Map.of("ceiling", new ReasonFact.MoneyFact(incomeCeiling))));
        }
        Money amount = Money.min(ctx.taxBeforeRelief(), maximum).floorAtZero();
        return new ReliefResult(amount, "the lesser of the tax and " + maximum, new Reason(
                ReasonCodes.RELIEF_LESSER_OF_TAX_AND_MAXIMUM, Map.of("maximum", new ReasonFact.MoneyFact(maximum))));
    }
}
