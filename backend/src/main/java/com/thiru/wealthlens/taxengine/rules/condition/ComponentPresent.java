package com.thiru.wealthlens.taxengine.rules.condition;

import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;

/**
 * A named pay line is present in this computation.
 *
 * <p>Presence, not amount — a rule that applies where house rent allowance is received applies
 * whether it is &#8377;1,000 or &#8377;10,00,000. Where an amount matters, that is a cap, not a
 * condition.
 *
 * @param code the pay line whose presence is tested
 */
public record ComponentPresent(ComponentCode code) implements Condition {

    public ComponentPresent {
        if (code == null) {
            throw new IllegalArgumentException("ComponentPresent must name a component");
        }
    }

    @Override
    public boolean holds(ConditionContext ctx) {
        return ctx.componentsPresent().contains(code);
    }

    @Override
    public String describe() {
        return code + " is present";
    }
}
