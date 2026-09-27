package com.thiru.wealthlens.taxengine.rules.component;

import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.registry.Provenance;
import java.util.HashSet;
import java.util.Set;

/**
 * One row of the component registry — a pay line, a perquisite, a contribution.
 *
 * <p>A registry rather than a Java enum, so that adding an allowance is a data change. The first
 * draft used a closed enum and the brief's central requirement was the price; what the enum was
 * defending against turns out to be better served here:
 *
 * <ul>
 *   <li><b>One vocabulary</b> (O-7): both the limits and the claim catalogue resolve against this
 *       registry, so {@code 80C} and {@code SECTION_80C} cannot both exist.
 *   <li><b>No silent addend</b> (O-15): gross for a head is a sum over a declared partition —
 *       {@code head == SALARIES} — so a misspelled but well-formed code is an error rather than
 *       something quietly added in ({@code D-CLOSEDSUM}). An enum would have caught the typo and
 *       left the real defect, which was summing every component regardless of code.
 *   <li><b>No switches</b> (O-25): semantics are attributes here, read rather than branched on. An
 *       enum makes a switch compile; it does not stop anyone writing one.
 * </ul>
 *
 * <p>Typo-safety comes from four layers instead of the compiler: pack load (invariant I7 — every
 * code a rule uses must exist here), request validation, the declared partition, and reachability.
 * That is stronger than the enum, because it also catches a <em>well-spelled</em> code that no rule
 * consumes.
 *
 * @param code the stable identifier
 * @param displayName what a user sees
 * @param head which income head it belongs to — the declared partition
 * @param kind what sort of thing it is
 * @param cashNature whether it is money in hand — {@code D-CASHFLAG}
 * @param treatment how it is treated for tax
 * @param feedsInto the rules that consume it
 * @param salaryBaseFor the salary bases it counts in, each saying whether only where the terms of
 *     employment provide (D-092); at most one entry per base
 * @param informationalOnly true where this component deliberately feeds no rule; reachability
 *     (invariant I3, extended) otherwise requires {@code feedsInto} to be non-empty, because a code
 *     nobody consumes is a figure the user enters and the computation ignores
 * @param lifecycle when it existed and what happens to drafts when it does not
 * @param provenance where the definition was sourced
 */
public record ComponentDefinition(
        ComponentCode code,
        String displayName,
        HeadCode head,
        ComponentKind kind,
        CashNature cashNature,
        TaxTreatment treatment,
        Set<RuleId> feedsInto,
        Set<SalaryBaseInclusion> salaryBaseFor,
        boolean informationalOnly,
        ComponentLifecycle lifecycle,
        Provenance provenance) {

    public ComponentDefinition {
        requireDescribed(code, displayName, head, kind, cashNature, treatment);
        if (lifecycle == null) {
            throw new IllegalArgumentException(code + " must declare a lifecycle");
        }
        if (provenance == null) {
            throw new IllegalArgumentException(code + " must say where it was sourced");
        }
        feedsInto = feedsInto == null ? Set.of() : Set.copyOf(feedsInto);
        salaryBaseFor = salaryBaseFor == null ? Set.of() : Set.copyOf(salaryBaseFor);
        requireOneEntryPerBase(code, salaryBaseFor);

        if (feedsInto.isEmpty() && !informationalOnly) {
            throw new IllegalArgumentException(
                    code + " feeds no rule and is not marked informationalOnly; a code nobody"
                            + " consumes is a figure the user enters and the computation ignores");
        }
    }

    private static void requireOneEntryPerBase(ComponentCode code, Set<SalaryBaseInclusion> salaryBaseFor) {
        Set<RuleId> seen = new HashSet<>();
        for (SalaryBaseInclusion inclusion : salaryBaseFor) {
            if (!seen.add(inclusion.rule())) {
                throw new IllegalArgumentException(code + " names salary base " + inclusion.rule()
                        + " twice; it either counts only where the terms provide or it does not");
            }
        }
    }

    private static void requireDescribed(
            ComponentCode code,
            String displayName,
            HeadCode head,
            ComponentKind kind,
            CashNature cashNature,
            TaxTreatment treatment) {
        if (code == null) {
            throw new IllegalArgumentException("A component definition must have a code");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException(code + " must have a display name");
        }
        if (head == null || kind == null || cashNature == null || treatment == null) {
            throw new IllegalArgumentException(
                    code + " must declare its head, kind, cash nature and treatment; each is read"
                            + " rather than branched on, so none may be absent");
        }
    }

    /** Whether this component counts toward take-home — {@code D-CASHFLAG}, H-15, O-13. */
    public boolean isCash() {
        return cashNature == CashNature.CASH;
    }
}
