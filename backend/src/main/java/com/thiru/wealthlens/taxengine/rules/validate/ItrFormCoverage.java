package com.thiru.wealthlens.taxengine.rules.validate;

import com.thiru.wealthlens.taxengine.rules.condition.Condition;
import com.thiru.wealthlens.taxengine.rules.condition.Not;
import com.thiru.wealthlens.taxengine.rules.condition.PersonType;
import com.thiru.wealthlens.taxengine.rules.filing.ItrFormSpec;
import com.thiru.wealthlens.taxengine.rules.id.ReturnKind;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * I19 and I20 ({@code DR 21}; W-090c2, D-529): a year's forms leave no one without a form and never offer two.
 *
 * <p>I19 counts the person types some regime of the year serves, as I5 does (D-062), times the return kinds the year's
 * forms name: a year that ships no forms names no kind, and form selection refuses it. I20 is decided on the rows'
 * structure, because no person exists at pack load (D-523 C4a). Two forms sharing a person type and a return kind
 * must be separated as r.164 separates its forms — one excludes the other, or one's positive conditions hold a
 * condition whose {@link Not} the other's hold.
 */
final class ItrFormCoverage {

    private ItrFormCoverage() {
    }

    static void check(RulePack pack, List<String> problems) {
        List<ItrFormSpec> forms = new ArrayList<>(pack.itrForms().values());
        forms.sort(Comparator.comparing(form -> form.code().value()));
        requireAFormForEveryone(pack, forms, problems);
        for (int i = 0; i < forms.size(); i++) {
            for (int j = i + 1; j < forms.size(); j++) {
                requireSeparated(forms.get(i), forms.get(j), problems);
            }
        }
    }

    private static void requireAFormForEveryone(RulePack pack, List<ItrFormSpec> forms, List<String> problems) {
        Set<PersonType> served = new TreeSet<>();
        pack.regimes().values().forEach(regime -> served.addAll(regime.appliesTo()));
        Set<ReturnKind> kinds = new TreeSet<>(Comparator.comparing(ReturnKind::value));
        forms.forEach(form -> kinds.addAll(form.returnKinds()));
        for (PersonType personType : served) {
            for (ReturnKind kind : kinds) {
                if (forms.stream().noneMatch(form -> form.appliesTo().contains(personType)
                        && form.returnKinds().contains(kind))) {
                    problems.add("I19: no form of " + pack.taxYear().label() + " serves " + personType + " for a "
                            + kind + " return");
                }
            }
        }
    }

    private static void requireSeparated(ItrFormSpec first, ItrFormSpec second, List<String> problems) {
        boolean shareAPerson = first.appliesTo().stream().anyMatch(second.appliesTo()::contains);
        boolean shareAKind = first.returnKinds().stream().anyMatch(second.returnKinds()::contains);
        if (!shareAPerson || !shareAKind || first.excludes().contains(second.code())
                || second.excludes().contains(first.code()) || opposes(first, second) || opposes(second, first)) {
            return;
        }
        problems.add("I20: forms " + first.code() + " and " + second.code() + " serve one person type and return kind,"
                + " and neither excludes the other nor holds the negation of the other's positive condition; both"
                + " could be offered");
    }

    private static boolean opposes(ItrFormSpec form, ItrFormSpec other) {
        for (Condition condition : form.positiveConditions()) {
            if (other.positiveConditions().contains(new Not(condition))) {
                return true;
            }
        }
        return false;
    }
}
