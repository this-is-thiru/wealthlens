package com.thiru.wealthlens.taxrules.condition;

import java.util.List;

/**
 * At least one limb must hold.
 *
 * <p>The shape {@code DED-80D-007} is in: the higher medical-insurance limit applies where
 * <em>either</em> the taxpayer <em>or</em> the person insured is sixty or over.
 *
 * <p><b>Short-circuits</b> on the first limb that holds. Consider that same rule where the taxpayer
 * is 65 and the insured person's age was never supplied. Short-circuiting allows the claim;
 * evaluating every limb would throw, blocking a valid claim to collect a fact that cannot change the
 * outcome. The first behaviour is right, and its price is that with missing facts the result depends
 * on limb order.
 *
 * <p>An empty list is rejected: vacuously false would silently deny every taxpayer.
 *
 * @param any the limbs, in the order they should be evaluated
 */
public record AnyOf(List<Condition> any) implements Condition {

    public AnyOf {
        if (any == null || any.isEmpty()) {
            throw new IllegalArgumentException(
                    "AnyOf must have at least one limb; an empty one would deny every taxpayer");
        }
        any = List.copyOf(any);
    }

    public static AnyOf of(Condition... limbs) {
        return new AnyOf(List.of(limbs));
    }

    @Override
    public boolean holds(ConditionContext ctx) {
        for (Condition limb : any) {
            if (limb.holds(ctx)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String describe() {
        return String.join(" or ", any.stream().map(Condition::describe).toList());
    }
}
