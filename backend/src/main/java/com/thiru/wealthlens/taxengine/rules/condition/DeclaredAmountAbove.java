package com.thiru.wealthlens.taxengine.rules.condition;

import com.thiru.wealthlens.taxengine.rules.id.DeclarationCode;
import com.thiru.wealthlens.taxengine.rules.money.Money;

/**
 * A declared amount exceeds a limit (W-090a1, D-517) — the law's "exceeding", so an amount equal to the limit does not
 * hold; its "does not exceed" is {@link Not}. An unanswered amount throws rather than read as nil.
 *
 * @param code the question, an amount row of the year's declaration catalogue
 * @param limit the limit, exclusive
 */
public record DeclaredAmountAbove(DeclarationCode code, Money limit) implements Condition {

    public DeclaredAmountAbove {
        if (code == null || limit == null) {
            throw new IllegalArgumentException("DeclaredAmountAbove needs a declaration and a limit");
        }
    }

    @Override
    public boolean holds(ConditionContext ctx) {
        return ctx.declarations().amount(code).isGreaterThan(limit);
    }

    @Override
    public String describe() {
        return code + " is above " + limit;
    }
}
