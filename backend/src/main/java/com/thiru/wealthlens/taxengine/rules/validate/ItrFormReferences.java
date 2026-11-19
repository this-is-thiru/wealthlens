package com.thiru.wealthlens.taxengine.rules.validate;

import com.thiru.wealthlens.taxengine.rules.condition.PersonType;
import com.thiru.wealthlens.taxengine.rules.filing.ItrFormSpec;
import com.thiru.wealthlens.taxengine.rules.id.ItrFormCode;
import com.thiru.wealthlens.taxengine.rules.id.ReturnKind;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * W-090b — a year's forms make one deterministic cascade. A form's exclusion names a form of the same year that comes
 * earlier, because r.164(4) excludes a person "to whom sub-rule (2) applies", an earlier sub-rule; and no two forms
 * that could both be tried for one person type and return kind share a cascade order, or which is tried first would
 * be an accident of the map.
 */
final class ItrFormReferences {

    private ItrFormReferences() {
    }

    static void check(RulePack pack, List<String> problems) {
        List<ItrFormSpec> forms = new ArrayList<>(pack.itrForms().values());
        forms.sort(Comparator.comparing(form -> form.code().value()));
        for (ItrFormSpec form : forms) {
            for (ItrFormCode excluded : form.excludes()) {
                requireEarlier(pack, form, excluded, problems);
            }
        }
        for (int i = 0; i < forms.size(); i++) {
            for (int j = i + 1; j < forms.size(); j++) {
                requireDistinctOrder(forms.get(i), forms.get(j), problems);
            }
        }
    }

    private static void requireEarlier(RulePack pack, ItrFormSpec form, ItrFormCode excluded, List<String> problems) {
        ItrFormSpec earlier = pack.itrForms().get(excluded);
        if (earlier == null) {
            problems.add("form " + form.code() + " is excluded by " + excluded + ", which is not a form of the year");
        } else if (earlier.cascadeOrder() >= form.cascadeOrder()) {
            problems.add("form " + form.code() + " (cascade " + form.cascadeOrder() + ") is excluded by " + excluded
                    + " (cascade " + earlier.cascadeOrder() + "); a form is excluded only by an earlier one");
        }
    }

    private static void requireDistinctOrder(ItrFormSpec first, ItrFormSpec second, List<String> problems) {
        if (first.cascadeOrder() != second.cascadeOrder()) {
            return;
        }
        List<PersonType> sharedTypes = first.appliesTo().stream().filter(second.appliesTo()::contains).toList();
        List<ReturnKind> sharedKinds = first.returnKinds().stream().filter(second.returnKinds()::contains).toList();
        if (!sharedTypes.isEmpty() && !sharedKinds.isEmpty()) {
            problems.add("forms " + first.code() + " and " + second.code() + " share cascade order "
                    + first.cascadeOrder() + " for " + sharedTypes + " and " + sharedKinds
                    + "; the cascade would not say which is tried first");
        }
    }
}
