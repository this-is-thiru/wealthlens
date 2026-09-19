package com.thiru.wealthlens.taxrules.validate;

import com.thiru.wealthlens.taxrules.component.ComponentDefinition;
import com.thiru.wealthlens.taxrules.condition.PersonType;
import com.thiru.wealthlens.taxrules.id.ComponentCode;
import com.thiru.wealthlens.taxrules.id.RegimeCode;
import com.thiru.wealthlens.taxrules.id.RuleId;
import com.thiru.wealthlens.taxrules.pack.ConditionSet;
import com.thiru.wealthlens.taxrules.pack.DisallowanceSet;
import com.thiru.wealthlens.taxrules.pack.RulePack;
import com.thiru.wealthlens.taxrules.regime.ConditionSetId;
import com.thiru.wealthlens.taxrules.regime.DisallowanceSetId;
import com.thiru.wealthlens.taxrules.regime.RegimeSpec;
import com.thiru.wealthlens.taxrules.temporal.RuleVersion;
import com.thiru.wealthlens.taxrules.temporal.Validity;
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
     * @throws PackValidationException listing every violation found, not just the first
     */
    public static void validate(RulePack pack) {
        List<String> problems = new ArrayList<>();

        checkRuleReferencesResolve(pack, problems);       // I2, generalised
        checkExactlyOneDefaultPerPersonType(pack, problems);  // I5
        checkRegimeSetReferencesResolve(pack, problems);  // I6
        checkComponentReferencesResolve(pack, problems);  // I7

        if (!problems.isEmpty()) {
            throw new PackValidationException(
                    "Pack " + pack.taxYear().label() + " has " + problems.size()
                            + " unresolved reference(s):\n  - " + String.join("\n  - ", problems));
        }
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
            component.salaryBaseFor().forEach(
                    rule -> require(known, rule, "component " + component.code() + " salaryBaseFor", problems));
        }
        for (DisallowanceSet set : pack.disallowanceSets().values()) {
            require(known, set.basis(), "disallowance set " + set.id() + " basis", problems);
            set.disallowedRules().forEach(
                    rule -> require(known, rule, "disallowance set " + set.id(), problems));
        }
        for (ConditionSet set : pack.conditionSets().values()) {
            require(known, set.basis(), "condition set " + set.id() + " basis", problems);
        }
        for (Map.Entry<RuleId, com.thiru.wealthlens.taxrules.pack.RuleRow> entry : pack.rules().entrySet()) {
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
     * I5 — exactly one default regime per person type, among regimes applicable that year.
     *
     * <p>Counted over <b>applicable</b> regimes only, because a lapsed regime that was once the
     * default must not make a live year look ambiguous. Two defaults means the engine picks one
     * arbitrarily; none means a taxpayer who elects nothing has no regime at all.
     */
    private static void checkExactlyOneDefaultPerPersonType(RulePack pack, List<String> problems) {
        for (PersonType personType : PersonType.values()) {
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
            } else if (defaults.isEmpty() && !pack.regimes().isEmpty()) {
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

    /** I7 — every component a rule's aggregate cap contributes from exists in the registry. */
    private static void checkComponentReferencesResolve(RulePack pack, List<String> problems) {
        Set<ComponentCode> known = pack.components().keySet();

        for (Map.Entry<RuleId, com.thiru.wealthlens.taxrules.pack.RuleRow> entry : pack.rules().entrySet()) {
            for (RuleVersion version : entry.getValue().versions()) {
                if (version.cap()
                        instanceof com.thiru.wealthlens.taxrules.cap.AggregateWithCeiling aggregate) {
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

    private static void require(Set<RuleId> known, RuleId referenced, String where, List<String> problems) {
        if (referenced != null && !known.contains(referenced)) {
            problems.add(where + " names rule " + referenced + ", which the pack does not define");
        }
    }
}
