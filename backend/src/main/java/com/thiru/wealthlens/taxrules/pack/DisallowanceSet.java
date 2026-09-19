package com.thiru.wealthlens.taxrules.pack;

import com.thiru.wealthlens.taxrules.id.RuleId;
import com.thiru.wealthlens.taxrules.regime.DisallowanceSetId;
import com.thiru.wealthlens.taxrules.registry.Provenance;
import java.util.Set;

/**
 * A named list of rules a regime does not allow — the s.205(1) pattern.
 *
 * <h2>Disallowed is not the same as absent</h2>
 *
 * <p>A rule missing from a regime and a rule <b>forbidden</b> in it look identical in the arithmetic
 * and are completely different to a taxpayer. &ldquo;House rent allowance is not available in the
 * default regime because s.202(2)(a)(i) disallows Schedule III Sl. No. 11&rdquo; is an answer. A
 * silent zero is not, and it invites the user to conclude the product forgot.
 *
 * <p>Shared and referenced by id because several regimes disallow the same long list, and
 * s.205(1) itself opens by applying to a set of regimes at once. One definition, changed in one
 * place; copies would drift.
 *
 * @param id the name regimes refer to it by
 * @param displayName what to call it in an explanation
 * @param disallowedRules the rules a regime referencing this set may not apply
 * @param basis the provision doing the disallowing, e.g. {@code REG-NEW-DISALLOW-001}
 * @param provenance where the list was read from
 */
public record DisallowanceSet(
        DisallowanceSetId id,
        String displayName,
        Set<RuleId> disallowedRules,
        RuleId basis,
        Provenance provenance) {

    public DisallowanceSet {
        if (id == null) {
            throw new IllegalArgumentException("A disallowance set must have an id");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException(id + " must have a display name; it reaches the user");
        }
        if (basis == null) {
            throw new IllegalArgumentException(
                    id + " must cite the rule that disallows; without it the product can say a"
                            + " deduction is unavailable but not why, which is the whole difference"
                            + " between disallowed and absent");
        }
        if (provenance == null) {
            throw new IllegalArgumentException(id + " must say where it was read from");
        }
        disallowedRules = disallowedRules == null ? Set.of() : Set.copyOf(disallowedRules);

        if (disallowedRules.isEmpty()) {
            throw new IllegalArgumentException(
                    id + " disallows nothing; a regime referencing it would be unaffected, so the"
                            + " reference is a mistake rather than a no-op");
        }
    }

    /** Whether a rule is forbidden by this set. */
    public boolean disallows(RuleId rule) {
        return disallowedRules.contains(rule);
    }
}
