package com.thiru.wealthlens.taxrules.condition;

import java.util.Set;

/**
 * The assessee is one of the kinds this rule applies to.
 *
 * <p>Regimes and return forms both declare a {@code Set<PersonType> appliesTo}
 * ({@code 17-regime-lifecycle.md} L2, {@code 21-itr-forms.md} I3), and this is the predicate form of
 * that declaration.
 *
 * @param types at least one; an empty set would be a rule that applies to nobody
 */
public record HasPersonType(Set<PersonType> types) implements Condition {

    public HasPersonType {
        if (types == null || types.isEmpty()) {
            throw new IllegalArgumentException("HasPersonType must name at least one type; an empty set applies to nobody");
        }
        types = Set.copyOf(types);
    }

    @Override
    public boolean holds(ConditionContext ctx) {
        return ctx.isAnyOf(types);
    }

    @Override
    public String describe() {
        return "the assessee is " + (types.size() == 1 ? types.iterator().next() : "one of " + types);
    }
}
