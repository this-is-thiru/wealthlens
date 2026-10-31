package com.thiru.wealthlens.taxengine.rules.condition;

/**
 * The assessee has income under a given head.
 *
 * <p>Written for s.202(4)(a)'s regime lock-in, which turns on <b>having</b> business or professional
 * income rather than on any amount of it. {@code REG-OPTOUT-YEARLY-001}: a salaried person may
 * switch regime every year precisely because that head is absent, and the moment it is present the
 * lock-in and the one-time-withdrawal rule attach — for every future year.
 *
 * <p>That is why this asks about presence, not size. &#8377;50,000 of freelance work is not a small
 * amount of business income for this purpose; it is business income.
 *
 * @param head the head whose presence is tested
 */
public record HasIncomeUnderHead(HeadCode head) implements Condition {

    public HasIncomeUnderHead {
        if (head == null) {
            throw new IllegalArgumentException("HasIncomeUnderHead must name a head");
        }
    }

    @Override
    public boolean holds(ConditionContext ctx) {
        return ctx.headsWithIncome().contains(head);
    }

    @Override
    public String describe() {
        return "there is income under the head " + head;
    }
}
