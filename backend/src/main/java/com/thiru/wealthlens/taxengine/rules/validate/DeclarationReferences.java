package com.thiru.wealthlens.taxengine.rules.validate;

import com.thiru.wealthlens.taxengine.rules.condition.AllOf;
import com.thiru.wealthlens.taxengine.rules.condition.AnyOf;
import com.thiru.wealthlens.taxengine.rules.condition.Condition;
import com.thiru.wealthlens.taxengine.rules.condition.Declared;
import com.thiru.wealthlens.taxengine.rules.condition.DeclaredAmountAbove;
import com.thiru.wealthlens.taxengine.rules.condition.Not;
import com.thiru.wealthlens.taxengine.rules.declaration.AnswerKind;
import com.thiru.wealthlens.taxengine.rules.declaration.DeclarationDefinition;
import com.thiru.wealthlens.taxengine.rules.filing.ItrFormBar;
import com.thiru.wealthlens.taxengine.rules.filing.ItrFormSpec;
import com.thiru.wealthlens.taxengine.rules.id.DeclarationCode;
import com.thiru.wealthlens.taxengine.rules.pack.ConditionSet;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * W-090a1 — every condition that reads a declaration, in a rule, a condition set or a form (W-090b), names a question of the year's catalogue, asked in the kind the
 * condition reads: {@link Declared} a yes-or-no question, {@link DeclaredAmountAbove} an amount. A condition naming a
 * question the year does not ask could never be answered, so the row it guards would refuse every person; one reading
 * the wrong kind would refuse every answer.
 */
final class DeclarationReferences {

    /** A declaration a condition reads, and the kind it reads it in. */
    private record Reading(DeclarationCode code, AnswerKind kind) {
    }

    private DeclarationReferences() {
    }

    static void check(RulePack pack, List<String> problems) {
        for (RuleRow row : pack.rules().values()) {
            row.versions().stream().map(RuleVersion::conditions).flatMap(List::stream)
                    .flatMap(DeclarationReferences::readings).distinct()
                    .forEach(reading -> requireAsked(pack, "rule " + row.id(), reading, problems));
        }
        for (ConditionSet set : pack.conditionSets().values()) {
            set.conditions().stream().flatMap(DeclarationReferences::readings).distinct()
                    .forEach(reading -> requireAsked(pack, "condition set " + set.id(), reading, problems));
        }
        for (ItrFormSpec form : pack.itrForms().values()) {
            formConditions(form).flatMap(DeclarationReferences::readings).distinct()
                    .forEach(reading -> requireAsked(pack, "form " + form.code(), reading, problems));
        }
    }

    private static void requireAsked(RulePack pack, String reader, Reading reading, List<String> problems) {
        Optional<DeclarationDefinition> asked = Optional.ofNullable(pack.declarations().get(reading.code()));
        if (asked.isEmpty()) {
            problems.add(reader + " reads declaration " + reading.code()
                    + ", which is not in the year's declaration catalogue");
        } else if (asked.get().answerKind() != reading.kind()) {
            problems.add(reader + " reads declaration " + reading.code() + " as " + reading.kind()
                    + ", but the catalogue asks it as " + asked.get().answerKind());
        }
    }

    /** Every condition a form holds: what it is for, what bars it, and when each manner of furnishing is open. */
    private static Stream<Condition> formConditions(ItrFormSpec form) {
        return Stream.of(
                        form.positiveConditions().stream(),
                        form.bars().stream().map(ItrFormBar::condition),
                        form.furnishing().stream().flatMap(option -> option.conditions().stream()))
                .flatMap(conditions -> conditions);
    }

    /** The declarations a condition reads, through {@code AllOf}, {@code AnyOf} and {@code Not}. */
    private static Stream<Reading> readings(Condition condition) {
        return switch (condition) {
            case Declared declared -> Stream.of(new Reading(declared.code(), AnswerKind.YES_NO));
            case DeclaredAmountAbove above -> Stream.of(new Reading(above.code(), AnswerKind.AMOUNT));
            case AllOf all -> all.all().stream().flatMap(DeclarationReferences::readings);
            case AnyOf any -> any.any().stream().flatMap(DeclarationReferences::readings);
            case Not not -> readings(not.condition());
            default -> Stream.empty();
        };
    }
}
