package com.thiru.wealthlens.taxrules.regime;

import com.thiru.wealthlens.taxrules.condition.PersonType;
import com.thiru.wealthlens.taxrules.id.RegimeCode;
import com.thiru.wealthlens.taxrules.id.RuleId;
import com.thiru.wealthlens.taxrules.id.TaxYearId;
import com.thiru.wealthlens.taxrules.registry.Provenance;
import com.thiru.wealthlens.taxrules.temporal.Provision;
import java.util.Optional;
import java.util.Set;

/**
 * A tax regime, entire, as one declarable row.
 *
 * <p>This record is the answer to &ldquo;what happens when a fourth regime arrives?&rdquo; Everything
 * that distinguishes one regime from another is a field here — its rate table, what it disallows,
 * who may use it, how it is elected, when it may be entered or applied, and which nodes it runs — so
 * a new regime is a pack row rather than a branch somewhere in the engine.
 *
 * <p>Three of those fields carry ideas that cost the design a revision each:
 *
 * <ul>
 *   <li>{@code lifecycle} holds <b>two</b> windows, because entry closing and the regime ending are
 *       different events, and ss.201/204 are in that state today.
 *   <li>{@code election} holds a {@link BindingSpec} rather than a lock-in flag, because s.202(4)
 *       binds a person with business income and not one without — the same regime, two limbs.
 *   <li>{@code pipeline} is per-regime, so a step that exists in one regime and not another is a
 *       different node set rather than an {@code if} inside a node ({@code D-REGIMESTEP}).
 * </ul>
 *
 * @param code the stable identifier, e.g. {@code S202_DEFAULT}
 * @param displayName what a user sees, e.g. "Default regime (s.202(1))"
 * @param provision1961 where this regime lives in the Income-tax Act 1961
 * @param provision2025 where it lives in the Income-tax Act 2025
 * @param appliesTo which kinds of assessee may use it
 * @param rateTable the rule holding its slab ladder
 * @param disallowances shared disallowance sets, by id — the s.205(1) pattern
 * @param eligibility shared condition sets, by id — the s.205(2) pattern
 * @param election how a taxpayer gets in or out
 * @param lifecycle when it may be entered and when applied
 * @param isDefault whether it applies with no action; exactly one regime per
 *     {@code (taxYear, PersonType)} may say true, which the pack validator checks (invariant I11)
 * @param pipeline which nodes it runs
 * @param behaviour a registered {@code RegimeBehaviour}, normally empty — L5's escape hatch
 * @param provenance where the regime was read from
 */
public record RegimeSpec(
        RegimeCode code,
        String displayName,
        Provision provision1961,
        Provision provision2025,
        Set<PersonType> appliesTo,
        RuleId rateTable,
        Set<DisallowanceSetId> disallowances,
        Set<ConditionSetId> eligibility,
        ElectionProtocol election,
        RegimeLifecycle lifecycle,
        boolean isDefault,
        PipelineSpec pipeline,
        Optional<BehaviourId> behaviour,
        Provenance provenance) {

    public RegimeSpec {
        requireIdentity(code, displayName, provision1961, provision2025);
        requireDeclarations(code, rateTable, election, lifecycle, pipeline, provenance);
        appliesTo = appliesTo == null ? Set.of() : Set.copyOf(appliesTo);
        disallowances = disallowances == null ? Set.of() : Set.copyOf(disallowances);
        eligibility = eligibility == null ? Set.of() : Set.copyOf(eligibility);
        behaviour = behaviour == null ? Optional.empty() : behaviour;
        requireConsistent(code, appliesTo, isDefault, election);
    }

    private static void requireIdentity(
            RegimeCode code, String displayName, Provision provision1961, Provision provision2025) {
        if (code == null) {
            throw new IllegalArgumentException("A regime must have a code");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException(code + " must have a display name; it reaches the user");
        }
        if (provision1961 == null || provision2025 == null) {
            throw new IllegalArgumentException(
                    code + " must state its place in both Acts; use Provision.absent or"
                            + " Provision.notYetVerified rather than leaving one out");
        }
    }

    private static void requireDeclarations(
            RegimeCode code,
            RuleId rateTable,
            ElectionProtocol election,
            RegimeLifecycle lifecycle,
            PipelineSpec pipeline,
            Provenance provenance) {
        if (rateTable == null) {
            throw new IllegalArgumentException(code + " must name its rate table");
        }
        if (election == null || lifecycle == null || pipeline == null) {
            throw new IllegalArgumentException(
                    code + " must declare its election protocol, lifecycle and pipeline");
        }
        if (provenance == null) {
            throw new IllegalArgumentException(code + " must say where it was read from");
        }
    }

    private static void requireConsistent(
            RegimeCode code, Set<PersonType> appliesTo, boolean isDefault, ElectionProtocol election) {
        if (appliesTo.isEmpty()) {
            throw new IllegalArgumentException(
                    code + " applies to nobody; a regime no assessee may use is a pack mistake");
        }
        if (isDefault && election.kind() != ElectionKind.DEFAULT_NO_ACTION) {
            throw new IllegalArgumentException(
                    code + " is marked isDefault but its election kind is " + election.kind()
                            + "; the default regime is the one that applies when nothing is done");
        }
    }

    /** Whether this regime may be used by a kind of assessee in a given year. */
    public boolean availableTo(PersonType personType, TaxYearId taxYear) {
        return appliesTo.contains(personType) && lifecycle.applicable(taxYear);
    }

    /** Whether a taxpayer could newly elect into this regime for a given year. */
    public boolean openToNewEntrants(PersonType personType, TaxYearId taxYear) {
        return appliesTo.contains(personType) && lifecycle.openToNewEntrants(taxYear);
    }
}
