package com.thiru.wealthlens.taxengine.rules.condition;

/**
 * The assessee owns at most this many house properties, a share in one counting as owning it.
 *
 * <p>r.164(2)(b) of the Income-tax Rules, 2026 gives ITR-1 to an assessee who "does not own more than two house
 * property"; the number is the form row's, never the code's (W-090c1a, D-524 C2a, D-525).
 *
 * @param limit the most the assessee may own; nought or more
 */
public record HousePropertiesOwnedAtMost(int limit) implements Condition {

    public HousePropertiesOwnedAtMost {
        if (limit < 0) {
            throw new IllegalArgumentException("A limit on house properties owned must be nought or more, not " + limit);
        }
    }

    @Override
    public boolean holds(ConditionContext ctx) {
        return ctx.housePropertiesOwned() <= limit;
    }

    @Override
    public String describe() {
        return "the assessee owns at most " + limit + " house properties";
    }
}
