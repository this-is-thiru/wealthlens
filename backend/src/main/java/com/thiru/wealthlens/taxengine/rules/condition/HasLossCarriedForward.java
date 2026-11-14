package com.thiru.wealthlens.taxengine.rules.condition;

import com.thiru.wealthlens.taxengine.rules.money.Money;

/**
 * A loss under the head is left to carry forward — r.164(2)(b) and (d)'s "no loss … to be carried forward under the
 * head" (W-090c1b2, D-528): the year's loss left and every carried-in loss still carried ({@link ReturnFacts}).
 *
 * @param head the head
 */
public record HasLossCarriedForward(HeadCode head) implements Condition {

    public HasLossCarriedForward {
        if (head == null) {
            throw new IllegalArgumentException("HasLossCarriedForward must name a head");
        }
    }

    @Override
    public boolean holds(ConditionContext ctx) {
        return ctx.returnFacts().lossToCarryForward(head).isGreaterThan(Money.ZERO);
    }

    @Override
    public String describe() {
        return "a loss under the head " + head + " is left to carry forward";
    }
}
