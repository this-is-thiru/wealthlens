package com.thiru.wealthlens.taxplanning.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.shared.exception.BadRequestException;
import com.thiru.wealthlens.taxengine.input.Declaration;
import com.thiru.wealthlens.taxengine.rules.id.DeclarationCode;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import org.junit.jupiter.api.Test;

/** A draft's {@code /filing/declarations} as stored and as the engine reads it (W-090a2, D-520). */
class DeclarationsBindingTest {

    private static DraftInputs withDeclarations(String declarations) {
        return DraftInputsMapper.read(DraftInputsMapper.text(JsonPatchApplier.apply(
                DraftInputsMapper.tree(DraftInputsMapper.blank()),
                DraftInputsMapper.tree("[{\"op\": \"add\", \"path\": \"/filing/declarations\", \"value\": " + declarations + "}]"))));
    }

    @Test
    void blank_whenRead_answersNothing() {
        // When / Then
        assertThat(DraftInputs.blank().filing().declarations()).isEmpty();
        assertThat(DraftInputsMapper.blank()).contains("\"declarations\":{}");
    }

    @Test
    void read_whenStoredBeforeDeclarationsExisted_answersNothing() {
        // When — a draft stored before W-090a2 has no declarations in its filing
        DraftInputs read = DraftInputsMapper.read("""
                {"entered": null, "brought": null, "filing": {"dueDate": null, "furnishedOn": null, "advanceTaxPaid": [],
                 "taxAtSource": null, "selfAssessmentPaid": []}}""");

        // Then
        assertThat(read.filing().declarations()).isEmpty();
    }

    @Test
    void read_whenEachKindIsTagged_bindsToTheEnginesAnswer() {
        // When
        DraftInputs read = withDeclarations("""
                {"DIRECTOR_IN_COMPANY": {"@type": "YesNo", "yes": false},
                 "AGRICULTURAL_INCOME": {"@type": "Amount", "amount": {"amountInRupees": 4000}}}""");

        // Then
        assertThat(read.filing().declarations()).containsEntry(
                new DeclarationCode("DIRECTOR_IN_COMPANY"), new Declaration.YesNo(false));
        assertThat(read.filing().declarations()).containsEntry(
                new DeclarationCode("AGRICULTURAL_INCOME"), new Declaration.Amount(Money.of("4000")));
    }

    @Test
    void requireBinds_whenAnAnswerHasNoKind_refusesNamingWhere() {
        // Given
        var tree = JsonPatchApplier.apply(DraftInputsMapper.tree(DraftInputsMapper.blank()), DraftInputsMapper.tree(
                "[{\"op\": \"add\", \"path\": \"/filing/declarations\", \"value\": {\"DIRECTOR_IN_COMPANY\": {\"yes\": true}}}]"));

        // When / Then
        assertThatThrownBy(() -> DraftInputsMapper.requireBinds(tree))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("/filing/declarations");
    }

    @Test
    void requireBinds_whenADeclaredAmountIsNegative_refuses() {
        // Given
        var tree = JsonPatchApplier.apply(DraftInputsMapper.tree(DraftInputsMapper.blank()), DraftInputsMapper.tree(
                "[{\"op\": \"add\", \"path\": \"/filing/declarations\", \"value\": {\"AGRICULTURAL_INCOME\":"
                        + " {\"@type\": \"Amount\", \"amount\": {\"amountInRupees\": -1}}}}]"));

        // When / Then
        assertThatThrownBy(() -> DraftInputsMapper.requireBinds(tree)).isInstanceOf(BadRequestException.class);
    }
}
