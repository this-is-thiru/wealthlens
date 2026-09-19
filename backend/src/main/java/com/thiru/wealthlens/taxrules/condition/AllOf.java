package com.thiru.wealthlens.taxrules.condition;

import java.util.List;

/**
 * Every limb must hold.
 *
 * <p><b>Short-circuits</b> on the first limb that fails, which is a semantic choice rather than an
 * optimisation: {@link Condition#holds} throws when a fact was never supplied, so stopping early
 * means a condition already settled does not demand information that cannot change it. The cost is
 * that when facts are missing the outcome depends on limb order, and rules should therefore list
 * their cheapest and most certain limbs first.
 *
 * <p>An empty list is rejected. The tidy answer — vacuously true — is the dangerous one: an empty
 * {@code AllOf} on a rule row would silently allow every taxpayer, which cannot be what a pack
 * author meant.
 *
 * @param all the limbs, in the order they should be evaluated
 */
public record AllOf(List<Condition> all) implements Condition {

    public AllOf {
        if (all == null || all.isEmpty()) {
            throw new IllegalArgumentException(
                    "AllOf must have at least one limb; an empty one would allow every taxpayer");
        }
        all = List.copyOf(all);
    }

    public static AllOf of(Condition... limbs) {
        return new AllOf(List.of(limbs));
    }

    @Override
    public boolean holds(ConditionContext ctx) {
        for (Condition limb : all) {
            if (!limb.holds(ctx)) {
                return false;
            }
        }
        return true;
    }

    /** The first limb that fails, or the whole conjunction where none does. */
    public java.util.Optional<Condition> firstFailing(ConditionContext ctx) {
        return all.stream().filter(limb -> !limb.holds(ctx)).findFirst();
    }

    @Override
    public String describe() {
        return String.join(" and ", all.stream().map(Condition::describe).toList());
    }
}
