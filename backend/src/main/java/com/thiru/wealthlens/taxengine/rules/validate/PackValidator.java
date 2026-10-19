package com.thiru.wealthlens.taxengine.rules.validate;

import com.thiru.wealthlens.taxengine.rules.component.ComponentDefinition;
import com.thiru.wealthlens.taxengine.rules.condition.PersonType;
import com.thiru.wealthlens.taxengine.rules.definition.DisallowanceBasis;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.pack.ConditionSet;
import com.thiru.wealthlens.taxengine.rules.pack.DisallowanceSet;
import com.thiru.wealthlens.taxengine.rules.pack.MidYearCombination;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.pack.RuleSelection;
import com.thiru.wealthlens.taxengine.rules.regime.BehaviourId;
import com.thiru.wealthlens.taxengine.rules.regime.ConditionSetId;
import com.thiru.wealthlens.taxengine.rules.regime.DisallowanceSetId;
import com.thiru.wealthlens.taxengine.rules.regime.RegimeBehaviourRegistry;
import com.thiru.wealthlens.taxengine.rules.regime.RegimeSpec;
import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalAxis;
import com.thiru.wealthlens.taxengine.rules.temporal.Validity;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Refuses a pack whose internal references do not resolve.
 *
 * <p>Every check here answers the same question: does this pack mean what it says? A rule naming a
 * component that does not exist, a regime pointing at a disallowance set nobody wrote, two regimes
 * both claiming to be the default — none of these throws at load unless something looks. They
 * produce a plausible number instead, and the first person to notice is a taxpayer.
 *
 * <p><b>All failures are reported together.</b> A pack author fixing one reference at a time, one
 * startup at a time, is a bad afternoon; the list is what makes a correction a single edit.
 *
 * <h2>What is checked elsewhere, and why not here</h2>
 *
 * <table>
 *   <caption>invariants I1–I7</caption>
 *   <tr><th>I1</th><td>enforced by {@code Provenance}'s constructor — an unverified row without a
 *       {@code whatToCheck} cannot be built, so a check here would be unreachable</td></tr>
 *   <tr><th>I4</th><td>enforced by the provider, which hashes the bytes it read. Re-hashing here
 *       would mean two places to keep in step</td></tr>
 *   <tr><th>I3</th><td><b>not yet checkable.</b> Reachability is "evaluated by some node", and the
 *       node-to-rule mapping lives in the engine, not the pack. A pack-only version would count a
 *       rule as reachable because a component mentions it, passing exactly the dead rule O-19
 *       describes</td></tr>
 * </table>
 */
public final class PackValidator {

    private PackValidator() {
    }

    /**
     * Validates a pack against a system that registers no behaviours — the state L5 recommends.
     *
     * @throws PackValidationException listing every violation found, not just the first
     */
    public static void validate(RulePack pack) {
        validate(pack, RegimeBehaviourRegistry.empty());
    }

    /**
     * @param behaviours what the running system implements, for invariant I9 and its converse
     * @throws PackValidationException listing every violation found, not just the first
     */
    public static void validate(RulePack pack, RegimeBehaviourRegistry behaviours) {
        List<String> problems = new ArrayList<>();
        checkBehavioursResolveBothWays(pack, behaviours, problems);  // I9 + D-ONEIMPL

        checkRuleReferencesResolve(pack, problems);       // I2, generalised
        checkExactlyOneDefaultPerPersonType(pack, problems);  // I5
        checkRegimeSetReferencesResolve(pack, problems);  // I6
        checkSetBasesNameTheirSets(pack, problems);       // D-165
        checkComponentReferencesResolve(pack, problems);  // I7
        checkYearIsCoveredWithoutGapOrOverlap(pack, problems);  // I13
        checkMidYearChangesAreDeclared(pack, problems);          // I14
        checkEveryVersionHasAnAmendment(pack, problems);         // I15
        checkRequiredRuleSetsHoldARow(pack, problems);           // D-252
        checkEveryRuleIsInARuleSet(pack, problems);              // D-252

        if (!problems.isEmpty()) {
            throw new PackValidationException(
                    "Pack " + pack.taxYear().label() + " has " + problems.size()
                            + " unresolved reference(s):\n  - " + String.join("\n  - ", problems));
        }
    }

    /** D-252 — a node reaches a row only through its set, so a row in none would be dead and silent about it. */
    private static void checkEveryRuleIsInARuleSet(RulePack pack, List<String> problems) {
        Set<RuleId> inASet = pack.membersOf(pack.ruleSets().keySet());
        pack.rules().keySet().stream()
                .filter(rule -> !inASet.contains(rule))
                .map(rule -> "rule " + rule + " names no rule set; a node reaches a row only through its set")
                .sorted()
                .forEach(problems::add);
    }

    /**
     * D-252 — a set that takes exactly one row holds one in this year: a node reads it unconditionally, so an empty
     * one would fail at the first computation rather than here. A provision of one year only is {@code AT_MOST_ONE}.
     */
    private static void checkRequiredRuleSetsHoldARow(RulePack pack, List<String> problems) {
        pack.ruleSets().values().stream()
                .filter(set -> set.selection() == RuleSelection.EXACTLY_ONE && set.members().isEmpty())
                .map(set -> "rule set " + set.id() + " takes exactly one row, and " + pack.taxYear().label()
                        + " holds none of its rows")
                .sorted()
                .forEach(problems::add);
    }

    /**
     * I2 — every {@code RuleId} the pack mentions exists in it.
     *
     * <p>{@code 02-TECH-SPEC.md} words this as "every {@code RuleId} a pipeline references", but a
     * pipeline holds node ids and the node-to-rule mapping is engine-side. This is the stronger
     * property the pack can actually check: every rule reference anywhere resolves.
     */
    private static void checkRuleReferencesResolve(RulePack pack, List<String> problems) {
        Set<RuleId> known = pack.rules().keySet();

        for (RegimeSpec regime : pack.regimes().values()) {
            require(known, regime.rateTable(), "regime " + regime.code() + " rate table", problems);
        }
        for (ComponentDefinition component : pack.components().values()) {
            component.feedsInto().forEach(
                    rule -> require(known, rule, "component " + component.code() + " feedsInto", problems));
            component.salaryBaseFor().forEach(inclusion -> require(
                    known, inclusion.rule(), "component " + component.code() + " salaryBaseFor", problems));
        }
        for (DisallowanceSet set : pack.disallowanceSets().values()) {
            require(known, set.basis(), "disallowance set " + set.id() + " basis", problems);
            set.disallowedRules().forEach(
                    rule -> require(known, rule, "disallowance set " + set.id(), problems));
        }
        for (ConditionSet set : pack.conditionSets().values()) {
            require(known, set.basis(), "condition set " + set.id() + " basis", problems);
        }
        for (Map.Entry<RuleId, com.thiru.wealthlens.taxengine.rules.pack.RuleRow> entry : pack.rules().entrySet()) {
            for (RuleVersion version : entry.getValue().versions()) {
                if (version.validity() instanceof Validity.PendingNotification pending) {
                    require(
                            known,
                            pending.interimRule(),
                            "rule " + entry.getKey() + " interim rule while unnotified",
                            problems);
                }
            }
        }
    }

    /**
     * I5 — exactly one default regime per person type the pack serves, among regimes applicable
     * that year.
     *
     * <p>Counted over <b>applicable</b> regimes only, because a lapsed regime that was once the
     * default must not make a live year look ambiguous. Two defaults means the engine picks one
     * arbitrarily; none means a taxpayer who elects nothing has no regime at all.
     *
     * <p>Only person types some regime in the pack names are counted (D-062): a pack that serves
     * no HUF owes an HUF no default, and one that names HUFs anywhere does. Named by any regime,
     * not only an applicable one, so a year whose regimes have all lapsed is still refused. Handed
     * an assessee type its pack does not serve, regime selection must refuse rather than pick.
     */
    private static void checkExactlyOneDefaultPerPersonType(RulePack pack, List<String> problems) {
        Set<PersonType> served = new TreeSet<>();
        pack.regimes().values().forEach(regime -> served.addAll(regime.appliesTo()));
        for (PersonType personType : served) {
            Set<RegimeCode> defaults = new LinkedHashSet<>();
            for (RegimeSpec regime : pack.regimes().values()) {
                if (regime.isDefault()
                        && regime.appliesTo().contains(personType)
                        && regime.lifecycle().applicable(pack.taxYear())) {
                    defaults.add(regime.code());
                }
            }
            if (defaults.size() > 1) {
                problems.add(
                        personType + " has " + defaults.size() + " default regimes ("
                                + new TreeSet<>(defaults.stream().map(RegimeCode::value).toList())
                                + "); the engine would pick one arbitrarily");
            } else if (defaults.isEmpty()) {
                problems.add(
                        personType + " has no default regime; a taxpayer who elects nothing would"
                                + " have no regime at all");
            }
        }
    }

    /** I6 — every disallowance and condition set a regime names exists. */
    private static void checkRegimeSetReferencesResolve(RulePack pack, List<String> problems) {
        Set<DisallowanceSetId> knownDisallowances = pack.disallowanceSets().keySet();
        Set<ConditionSetId> knownConditions = pack.conditionSets().keySet();

        for (RegimeSpec regime : pack.regimes().values()) {
            for (DisallowanceSetId id : regime.disallowances()) {
                if (!knownDisallowances.contains(id)) {
                    problems.add(
                            "regime " + regime.code() + " references disallowance set " + id
                                    + ", which the pack does not define; every deduction it was"
                                    + " meant to forbid would be silently allowed");
                }
            }
            for (ConditionSetId id : regime.eligibility()) {
                if (!knownConditions.contains(id)) {
                    problems.add(
                            "regime " + regime.code() + " references condition set " + id
                                    + ", which the pack does not define; every taxpayer would be"
                                    + " treated as eligible");
                }
            }
        }
    }

    /**
     * D-165 — a disallowance set's basis row is a {@link DisallowanceBasis} naming that set, in every version the pack
     * holds. The set says which provision forbids; the row says which set its words are the reason for. Checked both
     * ways because an explanation cites the basis: a basis row that is a cap, or that names another set, would put
     * the wrong section in front of the taxpayer.
     */
    private static void checkSetBasesNameTheirSets(RulePack pack, List<String> problems) {
        for (DisallowanceSet set : pack.disallowanceSets().values()) {
            if (!pack.rules().containsKey(set.basis())) {
                continue;  // reported by I2
            }
            for (RuleVersion version : pack.rule(set.basis()).versions()) {
                boolean namesTheSet = version.definition()
                        .filter(DisallowanceBasis.class::isInstance)
                        .map(DisallowanceBasis.class::cast)
                        .filter(basis -> basis.set().equals(set.id()))
                        .isPresent();
                if (!namesTheSet) {
                    problems.add("disallowance set " + set.id() + " rests on " + set.basis()
                            + ", whose version from " + version.validity() + " is not a DisallowanceBasis naming "
                            + set.id() + "; an explanation would cite a provision that is not the set's reason");
                }
            }
        }
    }

    /** I7 — every component a rule's aggregate cap contributes from exists in the registry. */
    private static void checkComponentReferencesResolve(RulePack pack, List<String> problems) {
        Set<ComponentCode> known = pack.components().keySet();

        for (Map.Entry<RuleId, com.thiru.wealthlens.taxengine.rules.pack.RuleRow> entry : pack.rules().entrySet()) {
            for (RuleVersion version : entry.getValue().versions()) {
                if (version.cap().isPresent() && version.cap().get()
                        instanceof com.thiru.wealthlens.taxengine.rules.cap.AggregateWithCeiling aggregate) {
                    for (ComponentCode code : aggregate.contributors()) {
                        if (!known.contains(code)) {
                            problems.add(
                                    "rule " + entry.getKey() + " aggregates component " + code
                                            + ", which is not in the registry; its contribution"
                                            + " would be missing from the total with no error");
                        }
                    }
                }
            }
        }
    }

    /**
     * I9 — every {@code BehaviourId} a pack names is implemented, and every implementation is named.
     *
     * <p>Both directions are load errors, for different reasons. A pack naming an unimplemented
     * behaviour would leave a regime's special handling silently not running — a plausible wrong
     * answer for everyone in that regime, in the one place the design allows arbitrary code. An
     * implementation no pack references is dead code that looks alive, which is the
     * {@code D-ONEIMPL} reachability rule extended to behaviours.
     *
     * <p>Unlike invariant I3, this reachability check <b>is</b> possible at load: the registry is in
     * hand, and a behaviour is referenced by a regime directly rather than through a node.
     */
    private static void checkBehavioursResolveBothWays(
            RulePack pack, RegimeBehaviourRegistry behaviours, List<String> problems) {

        Set<BehaviourId> referenced = new LinkedHashSet<>();
        for (RegimeSpec regime : pack.regimes().values()) {
            regime.behaviour().ifPresent(id -> {
                referenced.add(id);
                if (!behaviours.isRegistered(id)) {
                    problems.add(
                            "regime " + regime.code() + " names behaviour " + id
                                    + ", which no implementation registers; its special handling"
                                    + " would silently not run");
                }
            });
        }
        for (BehaviourId registered : behaviours.registeredIds()) {
            if (!referenced.contains(registered)) {
                problems.add(
                        "behaviour " + registered + " is registered but no regime in "
                                + pack.taxYear().label() + " references it; dead code that looks alive");
            }
        }
    }

    /**
     * I13 — a year-keyed rule's versions cover the tax year with no gap and no overlap.
     *
     * <p>A gap means the law said nothing for part of the year, which is not a state the law can be
     * in; an overlap means two versions claim the same day and {@code versionAt} silently takes the
     * first one listed. Both produce an answer. Neither produces the right one.
     *
     * <p>Only the {@code TAX_YEAR} axis is checked: a rule keyed on an acquisition or sanction date
     * legitimately has nothing in force during a given tax year.
     */
    private static void checkYearIsCoveredWithoutGapOrOverlap(RulePack pack, List<String> problems) {
        LocalDate start = pack.taxYear().startsOn();
        LocalDate end = pack.taxYear().endsOn();

        for (Map.Entry<RuleId, com.thiru.wealthlens.taxengine.rules.pack.RuleRow> entry : pack.rules().entrySet()) {
            if (entry.getValue().axis() != TemporalAxis.TAX_YEAR) {
                continue;
            }
            List<RuleVersion> inYear = entry.getValue().versions().stream()
                    .filter(version -> coversAnyOf(version, start, end))
                    .toList();

            if (inYear.isEmpty()) {
                problems.add(
                        "rule " + entry.getKey() + " has no version in force during "
                                + pack.taxYear().label() + "; the law cannot say nothing for a year");
                continue;
            }
            for (LocalDate day : List.of(start, end)) {
                long covering = inYear.stream().filter(v -> v.validity().contains(day)).count();
                if (covering == 0) {
                    problems.add(
                            "rule " + entry.getKey() + " covers no version on " + day
                                    + "; the tax year has a gap");
                } else if (covering > 1) {
                    problems.add(
                            "rule " + entry.getKey() + " has " + covering + " versions in force on "
                                    + day + "; versionAt would silently take the first listed");
                }
            }
        }
    }

    private static boolean coversAnyOf(RuleVersion version, LocalDate start, LocalDate end) {
        return version.validity().contains(start)
                || version.validity().contains(end)
                || windowStartsInside(version, start, end);
    }

    private static boolean windowStartsInside(RuleVersion version, LocalDate start, LocalDate end) {
        DateRange year = DateRange.between(start, end);
        return switch (version.validity()) {
            case Validity.Between between -> year.contains(between.from());
            case Validity.Retrospective retro -> year.contains(retro.deemedEffectiveFrom());
            case Validity.PendingNotification ignored -> false;
        };
    }

    /**
     * I14 — a rule that changes inside the year must say how to combine its versions.
     *
     * <p>{@code UNDETERMINED} is the honest default for a newly authored change, and it is refused
     * here rather than at computation time so the pack author hears about it before a taxpayer
     * does. Guessing produces a plausible number, only in the year the rule moved, that no golden
     * test written before the change would catch.
     */
    private static void checkMidYearChangesAreDeclared(RulePack pack, List<String> problems) {
        LocalDate start = pack.taxYear().startsOn();
        LocalDate end = pack.taxYear().endsOn();

        for (Map.Entry<RuleId, com.thiru.wealthlens.taxengine.rules.pack.RuleRow> entry : pack.rules().entrySet()) {
            com.thiru.wealthlens.taxengine.rules.pack.RuleRow row = entry.getValue();
            if (row.axis() != TemporalAxis.TAX_YEAR) {
                continue;
            }
            long inYear = row.versions().stream().filter(v -> coversAnyOf(v, start, end)).count();

            if (inYear > 1 && row.midYearCombination() == MidYearCombination.UNDETERMINED) {
                problems.add(
                        "rule " + entry.getKey() + " changes inside " + pack.taxYear().label()
                                + " but its midYearCombination is UNDETERMINED; the pack must say"
                                + " how the versions combine rather than let the engine guess");
            }
            if (row.midYearCombination() == MidYearCombination.STATUTORY) {
                require(
                        pack.rules().keySet(),
                        row.midYearCombinationRule(),
                        "rule " + entry.getKey() + " statutory combination",
                        problems);
            }
        }
    }

    /**
     * I15 — every rule version names the instrument that introduced it.
     *
     * <p>The ordered version list <b>is</b> the amendment history, and a version with no amendment
     * breaks it: the trace can state a figure but not where it came from, which is the one thing a
     * taxpayer challenging it needs.
     */
    private static void checkEveryVersionHasAnAmendment(RulePack pack, List<String> problems) {
        for (Map.Entry<RuleId, com.thiru.wealthlens.taxengine.rules.pack.RuleRow> entry : pack.rules().entrySet()) {
            List<RuleVersion> versions = entry.getValue().versions();
            for (int i = 0; i < versions.size(); i++) {
                if (versions.get(i).introducedBy() == null) {
                    problems.add(
                            "rule " + entry.getKey() + " version " + i + " names no AmendmentRef;"
                                    + " the trace could state its figure but not where it came from");
                }
            }
        }
    }

    private static void require(Set<RuleId> known, RuleId referenced, String where, List<String> problems) {
        if (referenced != null && !known.contains(referenced)) {
            problems.add(where + " names rule " + referenced + ", which the pack does not define");
        }
    }
}
