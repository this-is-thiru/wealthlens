package com.thiru.wealthlens.taxengine.rules.condition;

import com.thiru.wealthlens.taxengine.rules.money.Money;

/**
 * The year's figure under a head is a loss — r.164(2)(c)'s "does not have any loss under the head" read as the
 * year's loss, before set-off (W-090c1b1, D-527).
 *
 * @param head the head
 */
public record HasLossUnderHead(HeadCode head) implements Condition {

    public HasLossUnderHead {
        if (head == null) {
            throw new IllegalArgumentException("HasLossUnderHead must name a head");
        }
    }

    @Override
    public boolean holds(ConditionContext ctx) {
        return ctx.returnFacts().incomeUnder(head).isLessThan(Money.ZERO);
    }

    @Override
    public String describe() {
        return "there is a loss of the year under the head " + head;
    }
}
