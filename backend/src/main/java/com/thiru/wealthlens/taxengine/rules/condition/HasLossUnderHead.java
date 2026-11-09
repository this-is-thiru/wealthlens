package com.thiru.wealthlens.taxengine.rules.condition;

import com.thiru.wealthlens.taxengine.rules.money.Money;

/**
 * The year's figure under a head is a loss — r.164(2)(c)'s "does not have any loss under the head" read as the
 * year's loss, before set-off (W-090c1b1, D-527).
 *
 * <p>Capital gains are refused: the head's figure is its surviving income, never below nil, and its loss of the year
 * is a loss to carry forward ({@link HasLossCarriedForward}; W-090c1b2, D-528).
 *
 * @param head the head
 */
public record HasLossUnderHead(HeadCode head) implements Condition {

    public HasLossUnderHead {
        if (head == null) {
            throw new IllegalArgumentException("HasLossUnderHead must name a head");
        }
        if (head == HeadCode.CAPITAL_GAINS) {
            throw new IllegalArgumentException("HasLossUnderHead cannot read CAPITAL_GAINS, whose figure is never below"
                    + " nil; its loss of the year is read by HasLossCarriedForward");
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
