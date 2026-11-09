package com.thiru.wealthlens.taxengine.rules.condition;

import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsPart;
import com.thiru.wealthlens.taxengine.rules.money.Money;

/**
 * A capital-gains part's gain of the year is at most a limit — r.164(2)(d)'s "not exceeding ₹ 125000", the limit and
 * the part being the form's data (W-090c1b1, D-527). A part with no gain is nil.
 *
 * @param part the part
 * @param limit the most it may be
 */
public record CapitalGainsPartAtMost(CapitalGainsPart part, Money limit) implements Condition {

    public CapitalGainsPartAtMost {
        if (part == null || limit == null) {
            throw new IllegalArgumentException("CapitalGainsPartAtMost must name a part and a limit");
        }
    }

    @Override
    public boolean holds(ConditionContext ctx) {
        return !ctx.returnFacts().capitalGains().getOrDefault(part, Money.ZERO).isGreaterThan(limit);
    }

    @Override
    public String describe() {
        return "capital gains in " + part + " are at most " + limit;
    }
}
