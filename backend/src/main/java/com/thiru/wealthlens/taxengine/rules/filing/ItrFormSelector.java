package com.thiru.wealthlens.taxengine.rules.filing;

import com.thiru.wealthlens.taxengine.rules.condition.Condition;
import com.thiru.wealthlens.taxengine.rules.condition.ConditionContext;
import com.thiru.wealthlens.taxengine.rules.condition.PersonType;
import com.thiru.wealthlens.taxengine.rules.id.DeclarationCode;
import com.thiru.wealthlens.taxengine.rules.id.FurnishingManner;
import com.thiru.wealthlens.taxengine.rules.id.ItrFormCode;
import com.thiru.wealthlens.taxengine.rules.id.ReturnKind;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Chooses the form a return is furnished in by the year's own cascade (DR 21 I4; W-091, D-653). The forms are the
 * pack's rows: those serving the person and the kind of return, in {@code cascadeOrder}. A form is excluded where an
 * earlier form open to the return is one it excludes (r.164(4)'s "not being an individual to whom sub-rule (2)
 * applies"); otherwise its positive conditions and bars are tested. The first open form is chosen — the cascade's order,
 * not the simplest — unless an earlier form waits on an unanswered question, when none is (C3a). No form name is held
 * here: a form added to the pack is chosen for the facts it claims with no change to this class.
 */
public final class ItrFormSelector {

    private static final Comparator<DeclarationCode> BY_CODE = Comparator.comparing(DeclarationCode::value);

    private ItrFormSelector() {
    }

    /** Every candidate's verdict and the form chosen, for a return of {@code kind} by a {@code person}. */
    public static ItrSelection select(Collection<ItrFormSpec> forms, PersonType person, ReturnKind kind,
            ConditionContext facts) {
        List<ItrFormSpec> candidates = forms.stream()
                .filter(form -> form.appliesTo().contains(person))
                .filter(form -> form.returnKinds().contains(kind))
                .sorted(Comparator.comparingInt(ItrFormSpec::cascadeOrder))
                .toList();
        List<FormVerdict> verdicts = new ArrayList<>();
        candidates.forEach(form -> verdicts.add(verdictOn(form, verdicts, facts)));
        Optional<ItrFormSpec> chosen = chosen(candidates, verdicts);
        List<FurnishingManner> furnishing = chosen.map(form -> mannersOpen(form, facts)).orElse(List.of());
        return new ItrSelection(verdicts, chosen.map(ItrFormSpec::code), furnishing);
    }

    private static FormVerdict verdictOn(ItrFormSpec form, List<FormVerdict> earlier, ConditionContext facts) {
        Optional<ItrFormCode> excluder = earlier.stream()
                .filter(verdict -> verdict.standing() == FormVerdict.Standing.ELIGIBLE)
                .map(FormVerdict::form)
                .filter(form.excludes()::contains)
                .findFirst();
        if (excluder.isPresent()) {
            return FormVerdict.excluded(form.code(), excluder.get());
        }
        Set<DeclarationCode> unanswered = new TreeSet<>(BY_CODE);
        List<String> conditionsFailed = new ArrayList<>();
        for (Condition condition : form.positiveConditions()) {
            Set<DeclarationCode> missing = unansweredIn(condition, facts);
            if (!missing.isEmpty()) {
                unanswered.addAll(missing);
            } else if (!condition.holds(facts)) {
                conditionsFailed.add(condition.describe());
            }
        }
        List<ItrFormBar> barsHeld = new ArrayList<>();
        for (ItrFormBar bar : form.bars()) {
            Set<DeclarationCode> missing = unansweredIn(bar.condition(), facts);
            if (!missing.isEmpty()) {
                unanswered.addAll(missing);
            } else if (bar.condition().holds(facts)) {
                barsHeld.add(bar);
            }
        }
        return FormVerdict.tested(form.code(), conditionsFailed, barsHeld, unanswered);
    }

    private static Set<DeclarationCode> unansweredIn(Condition condition, ConditionContext facts) {
        return condition.declarationsRead().stream()
                .filter(code -> !facts.declarations().isAnswered(code))
                .collect(Collectors.toSet());
    }

    /** The first open form in cascade order, unless a form before it is undetermined. */
    private static Optional<ItrFormSpec> chosen(List<ItrFormSpec> candidates, List<FormVerdict> verdicts) {
        for (int i = 0; i < verdicts.size(); i++) {
            FormVerdict.Standing standing = verdicts.get(i).standing();
            if (standing == FormVerdict.Standing.UNDETERMINED) {
                return Optional.empty();
            }
            if (standing == FormVerdict.Standing.ELIGIBLE) {
                return Optional.of(candidates.get(i));
            }
        }
        return Optional.empty();
    }

    private static List<FurnishingManner> mannersOpen(ItrFormSpec form, ConditionContext facts) {
        return form.furnishing().stream()
                .filter(option -> option.conditions().stream().allMatch(condition -> condition.holds(facts)))
                .map(FurnishingOption::manner)
                .toList();
    }
}
