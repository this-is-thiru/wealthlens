package com.thiru.wealthlens.taxengine.rules.validate;

import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsPart;
import com.thiru.wealthlens.taxengine.rules.filing.FieldSource;
import com.thiru.wealthlens.taxengine.rules.filing.ItrFormSpec;
import com.thiru.wealthlens.taxengine.rules.filing.ScheduleCell;
import com.thiru.wealthlens.taxengine.rules.id.CellCode;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * I21 (DR 21 I8; W-092, D-654) — every cell a form's schedules place reads what the year's pack holds: each rule of a
 * {@code FromRules} is a rule of the year, each {@code FromCapitalGainsPart} a part the year declares, and each term of
 * a {@code SumOf} a cell the same form places. A cell reading what the year lacks would always show "unavailable", which
 * hides a misspelt code behind a true-looking reason. A {@code FromNodes} node is checked against the shipped nodes by
 * {@code ItrSchedulesTest}, since a pack holds no node registry.
 */
final class ItrScheduleReferences {

    private ItrScheduleReferences() {
    }

    static void check(RulePack pack, List<String> problems) {
        Set<CapitalGainsPart> declared = ReturnFactReferences.declaredParts(pack);
        for (ItrFormSpec form : pack.itrForms().values()) {
            Set<CellCode> placed = new HashSet<>();
            form.schedules().forEach(schedule -> schedule.fields().forEach(field -> placed.add(field.cell().code())));
            form.schedules().forEach(schedule -> schedule.fields().forEach(field ->
                    checkCell(pack, declared, placed, "form " + form.code() + " " + schedule.code() + " " + field.path(),
                            field.cell(), problems)));
        }
    }

    private static void checkCell(RulePack pack, Set<CapitalGainsPart> declared,
            Set<CellCode> placed, String reader, ScheduleCell cell, List<String> problems) {
        switch (cell.source()) {
            case FieldSource.FromRules rules -> rules.rules().stream().filter(rule -> !pack.rules().containsKey(rule))
                    .forEach(rule -> problems.add(reader + " (" + cell.code() + ") reads rule " + rule
                            + ", which is not a rule of " + pack.taxYear().label() + " (I21)"));
            case FieldSource.FromCapitalGainsParts parts -> parts.parts().stream().filter(part -> !declared.contains(part))
                    .forEach(part -> problems.add(reader + " (" + cell.code() + ") reads capital-gains part " + part
                            + ", which the year's CapitalGainsParts does not declare (I21)"));
            case FieldSource.SumOf sum -> sum.terms().stream().filter(term -> !placed.contains(term.cell()))
                    .forEach(term -> problems.add(reader + " (" + cell.code() + ") sums cell " + term.cell()
                            + ", which the form does not place (I21)"));
            case FieldSource.FromNodes nodes -> {
                // checked against the shipped nodes by a test: a pack holds no node registry
            }
            case FieldSource.FromTaxBasis basis -> {
                // a special rate is a run's code, checked by the persona tests
            }
            case FieldSource.ManualEntry manual -> {
                // reads nothing
            }
        }
    }
}
