package com.thiru.wealthlens.taxengine.rules.condition;

/**
 * The negation of a condition.
 *
 * <p>Note what it does <b>not</b> do: it negates the answer, never the failure. If the inner
 * condition throws because a fact was never supplied, this throws too. Turning a missing fact into
 * {@code true} would be the worst available behaviour — a deduction allowed because nobody knew
 * whether the taxpayer qualified.
 *
 * @param condition the condition to negate
 */
public record Not(Condition condition) implements Condition {

    public Not {
        if (condition == null) {
            throw new IllegalArgumentException("Not must wrap a condition");
        }
    }

    @Override
    public boolean holds(ConditionContext ctx) {
        return !condition.holds(ctx);
    }

    @Override
    public String describe() {
        return "not (" + condition.describe() + ")";
    }
}
