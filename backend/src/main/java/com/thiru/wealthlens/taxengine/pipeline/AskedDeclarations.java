package com.thiru.wealthlens.taxengine.pipeline;

import com.thiru.wealthlens.taxengine.input.Declaration;
import com.thiru.wealthlens.taxengine.rules.condition.Declarations;
import com.thiru.wealthlens.taxengine.rules.declaration.AnswerKind;
import com.thiru.wealthlens.taxengine.rules.declaration.DeclarationDefinition;
import com.thiru.wealthlens.taxengine.rules.id.DeclarationCode;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The person's answers checked against the year's declaration catalogue (W-090a1, W-090a2): an answer to a question
 * the year does not ask, or in the wrong kind, is refused naming the question, since no condition would read it and the
 * person would believe it counted. One check for both callers — a run, which reads the answers as the rules do, and a
 * draft saved, which is refused before it is stored (D-520 C1a).
 *
 * <p>A refusal names the question and the kind of answer, never the answer: an amount is the person's own figure
 * ({@code DR 14} O1.1).
 */
public final class AskedDeclarations {

    private AskedDeclarations() {
    }

    /**
     * The answers as the rules read them.
     *
     * @throws IllegalArgumentException if an answer names a question the pack does not ask, or answers it in the wrong
     *     kind
     */
    public static Declarations of(Map<DeclarationCode, Declaration> answers, RulePack pack) {
        Map<DeclarationCode, Boolean> yesNo = new LinkedHashMap<>();
        Map<DeclarationCode, Money> amounts = new LinkedHashMap<>();
        answers.forEach((code, answer) -> {
            DeclarationDefinition asked = Optional.ofNullable(pack.declarations().get(code))
                    .orElseThrow(() -> new IllegalArgumentException("The declaration " + code + " is not a question "
                            + pack.taxYear().label() + " asks; it asks " + pack.declarations().keySet()));
            switch (answer) {
                case Declaration.YesNo yes when asked.answerKind() == AnswerKind.YES_NO -> yesNo.put(code, yes.yes());
                case Declaration.Amount amount when asked.answerKind() == AnswerKind.AMOUNT ->
                        amounts.put(code, amount.amount());
                default -> throw new IllegalArgumentException("The declaration " + code + " is answered as "
                        + kindOf(answer) + ", but " + pack.taxYear().label() + " asks it as " + asked.answerKind());
            }
        });
        return new Declarations(yesNo, amounts);
    }

    private static AnswerKind kindOf(Declaration answer) {
        return switch (answer) {
            case Declaration.YesNo _ -> AnswerKind.YES_NO;
            case Declaration.Amount _ -> AnswerKind.AMOUNT;
        };
    }
}
