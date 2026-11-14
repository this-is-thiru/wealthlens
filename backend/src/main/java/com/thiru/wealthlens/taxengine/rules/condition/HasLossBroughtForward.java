package com.thiru.wealthlens.taxengine.rules.condition;

import com.thiru.wealthlens.taxengine.rules.money.Money;

/**
 * A loss under the head was brought forward into the year — r.164(2)(b) and (d)'s "no loss brought forward … under
 * the head" (W-090c1b2, D-528). Brought forward is what the law still carries into the year ({@link ReturnFacts}).
 *
 * @param head the head
 */
public record HasLossBroughtForward(HeadCode head) implements Condition {

    public HasLossBroughtForward {
        if (head == null) {
            throw new IllegalArgumentException("HasLossBroughtForward must name a head");
        }
    }

    @Override
    public boolean holds(ConditionContext ctx) {
        return ctx.returnFacts().lossBroughtForward(head).isGreaterThan(Money.ZERO);
    }

    @Override
    public String describe() {
        return "a loss under the head " + head + " was brought forward into the year";
    }
}
