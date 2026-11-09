package com.thiru.wealthlens.taxengine.rules.declaration;

import com.thiru.wealthlens.taxengine.rules.id.DeclarationCode;
import com.thiru.wealthlens.taxengine.rules.registry.Provenance;
import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;

/**
 * One row of the declaration catalogue (W-090a1, D-517): a question the return asks the person and the engine cannot
 * compute — assets outside India, a directorship. A fact the person states, never a label the law derives from it, so
 * a bar that reads it is data ({@code Declared}, {@code DeclaredAmountAbove}) and a question the law adds is a new row.
 * The clause of the law that asks it is in the provenance (D-518 C2a).
 *
 * @param code the question
 * @param question what the person is asked
 * @param answerKind whether the answer is yes or no, or an amount
 * @param validity when the law asks it
 * @param provenance where it was sourced, naming the clause
 */
public record DeclarationDefinition(
        DeclarationCode code, String question, AnswerKind answerKind, DateRange validity, Provenance provenance) {

    public DeclarationDefinition {
        if (code == null) {
            throw new IllegalArgumentException("A declaration definition must have a code");
        }
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException(code + " must state its question");
        }
        if (answerKind == null || validity == null || provenance == null) {
            throw new IllegalArgumentException(code + " must say how it is answered, when it is asked and its source");
        }
    }
}
