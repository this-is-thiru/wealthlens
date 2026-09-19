package com.thiru.wealthlens.taxrules.pack;

import com.thiru.wealthlens.taxrules.condition.Condition;
import com.thiru.wealthlens.taxrules.condition.ConditionContext;
import com.thiru.wealthlens.taxrules.id.RuleId;
import com.thiru.wealthlens.taxrules.regime.ConditionSetId;
import com.thiru.wealthlens.taxrules.registry.Provenance;
import java.util.List;
import java.util.Optional;

/**
 * A named list of conditions a taxpayer must meet — the s.205(2) pattern.
 *
 * <p>The counterpart of {@link DisallowanceSet}: eligibility is shared between regimes and
 * referenced by id for the same reason, so it is defined once and changed in one place.
 *
 * <p>{@link #firstUnmet} exists because a refusal has to be explainable. Reporting that a taxpayer
 * is ineligible is half an answer; reporting <em>which</em> condition failed is the half they can
 * act on.
 *
 * @param id the name regimes refer to it by
 * @param displayName what to call it in an explanation
 * @param conditions all of which must hold, in the order they should be evaluated
 * @param basis the provision imposing them
 * @param provenance where they were read from
 */
public record ConditionSet(
        ConditionSetId id,
        String displayName,
        List<Condition> conditions,
        RuleId basis,
        Provenance provenance) {

    public ConditionSet {
        if (id == null) {
            throw new IllegalArgumentException("A condition set must have an id");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException(id + " must have a display name; it reaches the user");
        }
        if (basis == null) {
            throw new IllegalArgumentException(id + " must cite the provision imposing it");
        }
        if (provenance == null) {
            throw new IllegalArgumentException(id + " must say where it was read from");
        }
        conditions = conditions == null ? List.of() : List.copyOf(conditions);

        if (conditions.isEmpty()) {
            throw new IllegalArgumentException(
                    id + " has no conditions; a regime referencing it would be unconditionally"
                            + " eligible, so the reference is a mistake rather than a no-op");
        }
    }

    /** Whether every condition holds. */
    public boolean isSatisfiedBy(ConditionContext ctx) {
        return conditions.stream().allMatch(condition -> condition.holds(ctx));
    }

    /**
     * The first condition that does not hold, for an explanation.
     *
     * <p>Short-circuits, like {@code AllOf}: a condition already settled does not demand facts that
     * cannot change it.
     */
    public Optional<Condition> firstUnmet(ConditionContext ctx) {
        return conditions.stream().filter(condition -> !condition.holds(ctx)).findFirst();
    }
}
