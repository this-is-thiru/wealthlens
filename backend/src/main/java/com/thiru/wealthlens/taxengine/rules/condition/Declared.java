package com.thiru.wealthlens.taxengine.rules.condition;

import com.thiru.wealthlens.taxengine.rules.id.DeclarationCode;

/**
 * The person answered yes to a declaration, such as holding a directorship (W-090a1, D-517). Its "no" is
 * {@link Not}; an unanswered question is neither, and throws.
 *
 * @param code the question, a yes-or-no row of the year's declaration catalogue
 */
public record Declared(DeclarationCode code) implements Condition {

    public Declared {
        if (code == null) {
            throw new IllegalArgumentException("Declared must name a declaration");
        }
    }

    @Override
    public boolean holds(ConditionContext ctx) {
        return ctx.declarations().isYes(code);
    }

    @Override
    public String describe() {
        return code + " is declared";
    }
}
