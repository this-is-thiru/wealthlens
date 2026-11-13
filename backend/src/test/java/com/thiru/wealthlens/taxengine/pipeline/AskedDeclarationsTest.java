package com.thiru.wealthlens.taxengine.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.Declaration;
import com.thiru.wealthlens.taxengine.rules.condition.Declarations;
import com.thiru.wealthlens.taxengine.rules.id.DeclarationCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The answers checked against TY 2026-27's shipped questions, r.164(3) and (6) (W-090a2, D-520). */
class AskedDeclarationsTest {

    private static final TaxYearId TY_2025_26 = TaxYearId.of(2025);
    private static final TaxYearId TY_2026_27 = TaxYearId.of(2026);
    private static final ShippedRulePacks PACKS = ShippedRulePacks.compile("rulepacks", Set.of(TY_2025_26, TY_2026_27));
    private static final RulePack PACK = PACKS.load(TY_2026_27);
    private static final DeclarationCode DIRECTOR = new DeclarationCode("DIRECTOR_IN_COMPANY");
    private static final DeclarationCode AGRICULTURE = new DeclarationCode("AGRICULTURAL_INCOME");

    @Test
    void of_whenShippedQuestionsAreAnsweredInTheirKinds_readsThem() {
        // When
        Declarations read = AskedDeclarations.of(Map.of(DIRECTOR, new Declaration.YesNo(true),
                AGRICULTURE, new Declaration.Amount(Money.of("4000"))), PACK);

        // Then
        assertThat(read.isYes(DIRECTOR)).isTrue();
        assertThat(read.amount(AGRICULTURE)).isEqualTo(Money.of("4000"));
    }

    @Test
    void of_whenTheAnswerIsOfTheWrongKind_namesTheKindNeverTheAmount() {
        // Given — an amount given to a yes-or-no question
        Map<DeclarationCode, Declaration> answers = Map.of(DIRECTOR, new Declaration.Amount(Money.of("98765")));

        // When / Then — DR 14 O1.1: a refusal names where, never the value
        assertThatThrownBy(() -> AskedDeclarations.of(answers, PACK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The declaration DIRECTOR_IN_COMPANY is answered as AMOUNT, but 2026-27 asks it as YES_NO")
                .hasMessageNotContaining("98765");
    }

    @Test
    void of_whenTheYearDoesNotAskTheQuestion_refusesTheAnswer() {
        // Given — r.164(3)(n)'s question is TY 2026-27's; TY 2025-26's r.12 has no such item (W-090d, D-652)
        RulePack earlier = PACKS.load(TY_2025_26);
        DeclarationCode specialCase = new DeclarationCode("SPECIAL_CASE_INCOME");

        // When / Then
        assertThat(PACK.declarations()).containsKey(specialCase);
        assertThatThrownBy(() -> AskedDeclarations.of(Map.of(specialCase, new Declaration.YesNo(false)), earlier))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SPECIAL_CASE_INCOME is not a question 2025-26 asks");
    }
}
