package com.thiru.wealthlens.taxengine.rules.condition;

import com.thiru.wealthlens.taxengine.rules.id.DeclarationCode;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Map;

/**
 * The person's answers to the year's declarations, as a condition reads them (W-090a1, D-517): yes or no by code, and
 * amounts by code. The inputs carry the answers as {@code Declaration}; they arrive here as plain values because the
 * rules vocabulary may not depend on the inputs, as {@link ItemFacts} does for an item.
 *
 * <p>A question with no answer <b>throws</b> when asked for, naming it, as every lookup in {@link ConditionContext}
 * does: reading an unanswered question as "no" would let a bar pass silently ({@code D-NOSILENT}).
 *
 * @param yesNo each yes-or-no answer, by question
 * @param amounts each amount answer, by question
 */
public record Declarations(Map<DeclarationCode, Boolean> yesNo, Map<DeclarationCode, Money> amounts) {

    /** No question answered. */
    public static final Declarations NONE = new Declarations(Map.of(), Map.of());

    public Declarations {
        if (yesNo == null || amounts == null) {
            throw new IllegalArgumentException("Declarations needs its yes-or-no answers and amounts, empty for none");
        }
        yesNo = Map.copyOf(yesNo);
        amounts = Map.copyOf(amounts);
        for (DeclarationCode code : yesNo.keySet()) {
            if (amounts.containsKey(code)) {
                throw new IllegalArgumentException(code + " is answered both yes-or-no and with an amount");
            }
        }
    }

    /** Whether the person answered the question, in either kind. */
    public boolean isAnswered(DeclarationCode code) {
        return yesNo.containsKey(code) || amounts.containsKey(code);
    }

    /**
     * Whether the person answered yes.
     *
     * @throws IllegalArgumentException if the question was not answered yes or no
     */
    public boolean isYes(DeclarationCode code) {
        Boolean answer = yesNo.get(code);
        if (answer == null) {
            throw new IllegalArgumentException("The question " + code + " has no yes-or-no answer; the person must"
                    + " answer it, since an unanswered question is not a no");
        }
        return answer;
    }

    /**
     * The amount the person declared.
     *
     * @throws IllegalArgumentException if the question was not answered with an amount
     */
    public Money amount(DeclarationCode code) {
        Money answer = amounts.get(code);
        if (answer == null) {
            throw new IllegalArgumentException("The question " + code + " has no amount answer; the person must"
                    + " answer it, since an unanswered amount is not nil");
        }
        return answer;
    }
}
