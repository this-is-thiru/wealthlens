package com.thiru.wealthlens.taxengine.rules.validate;

import com.thiru.wealthlens.taxengine.rules.condition.AllOf;
import com.thiru.wealthlens.taxengine.rules.condition.AnyOf;
import com.thiru.wealthlens.taxengine.rules.condition.CapitalGainsOnlyInParts;
import com.thiru.wealthlens.taxengine.rules.condition.CapitalGainsPartAtMost;
import com.thiru.wealthlens.taxengine.rules.condition.Condition;
import com.thiru.wealthlens.taxengine.rules.condition.DeductionAllowedUnder;
import com.thiru.wealthlens.taxengine.rules.condition.Not;
import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsPart;
import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsParts;
import com.thiru.wealthlens.taxengine.rules.filing.ItrFormBar;
import com.thiru.wealthlens.taxengine.rules.filing.ItrFormSpec;
import com.thiru.wealthlens.taxengine.rules.pack.ConditionSet;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * W-090c1b1 — a condition reading a computed return's facts names what the year's pack has: every row a
 * {@link DeductionAllowedUnder} names is a rule of the pack, and every part a capital-gains condition names is one the
 * year's {@code CapitalGainsParts} declares (D-309). A row or part the year lacks could never be read, so the bar it
 * belongs to would silently never bar.
 */
final class ReturnFactReferences {

    private ReturnFactReferences() {
    }

    static void check(RulePack pack, List<String> problems) {
        Set<CapitalGainsPart> declared = declaredParts(pack);
        for (RuleRow row : pack.rules().values()) {
            row.versions().stream().map(RuleVersion::conditions).flatMap(List::stream)
                    .forEach(condition -> checkCondition(pack, declared, "rule " + row.id(), condition, problems));
        }
        for (ConditionSet set : pack.conditionSets().values()) {
            set.conditions().forEach(
                    condition -> checkCondition(pack, declared, "condition set " + set.id(), condition, problems));
        }
        for (ItrFormSpec form : pack.itrForms().values()) {
            formConditions(form).forEach(
                    condition -> checkCondition(pack, declared, "form " + form.code(), condition, problems));
        }
    }

    private static void checkCondition(RulePack pack, Set<CapitalGainsPart> declared, String reader,
            Condition condition, List<String> problems) {
        switch (condition) {
            case DeductionAllowedUnder allowed -> allowed.rows().stream().filter(row -> !pack.rules().containsKey(row))
                    .forEach(row -> problems.add(reader + " reads the deduction of row " + row
                            + ", which is not a rule of " + pack.taxYear().label()));
            case CapitalGainsOnlyInParts only -> only.parts().forEach(
                    part -> requireDeclared(declared, reader, part, problems));
            case CapitalGainsPartAtMost atMost -> requireDeclared(declared, reader, atMost.part(), problems);
            case AllOf all -> all.all().forEach(limb -> checkCondition(pack, declared, reader, limb, problems));
            case AnyOf any -> any.any().forEach(limb -> checkCondition(pack, declared, reader, limb, problems));
            case Not not -> checkCondition(pack, declared, reader, not.condition(), problems);
            default -> {
                // reads no row and no part
            }
        }
    }

    private static void requireDeclared(Set<CapitalGainsPart> declared, String reader, CapitalGainsPart part,
            List<String> problems) {
        if (!declared.contains(part)) {
            problems.add(reader + " reads capital-gains part " + part + ", which the year's CapitalGainsParts does"
                    + " not declare (D-309)");
        }
    }

    static Set<CapitalGainsPart> declaredParts(RulePack pack) {
        Set<CapitalGainsPart> parts = new LinkedHashSet<>();
        pack.rules().values().stream().flatMap(row -> row.versions().stream())
                .filter(version -> PackValidator.coversAnyOf(version, pack.taxYear().startsOn(), pack.taxYear().endsOn()))
                .flatMap(version -> version.definition().stream())
                .filter(CapitalGainsParts.class::isInstance).map(CapitalGainsParts.class::cast)
                .forEach(declaration -> declaration.parts().forEach(declared -> parts.add(declared.part())));
        return parts;
    }

    private static Stream<Condition> formConditions(ItrFormSpec form) {
        return Stream.of(
                        form.positiveConditions().stream(),
                        form.bars().stream().map(ItrFormBar::condition),
                        form.furnishing().stream().flatMap(option -> option.conditions().stream()))
                .flatMap(conditions -> conditions);
    }
}
