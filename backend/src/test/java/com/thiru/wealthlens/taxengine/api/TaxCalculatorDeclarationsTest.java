package com.thiru.wealthlens.taxengine.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.Declaration;
import com.thiru.wealthlens.taxengine.rules.id.DeclarationCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The year's questions as the api serves them, and the check a draft's save makes (W-090a2, D-520 C1a, C2a). */
class TaxCalculatorDeclarationsTest {

    private static final TaxYearId TY_2025_26 = TaxYearId.of(2025);
    private static final TaxYearId TY_2026_27 = TaxYearId.of(2026);
    private static final TaxCalculator CALCULATOR =
            new TaxCalculator(ShippedRulePacks.compile("rulepacks", Set.of(TY_2025_26, TY_2026_27)));

    @Test
    void declarations_whenTy2026_listsR164sThirteenInCodeOrder() {
        // When
        List<DeclarationQuestion> questions = CALCULATOR.declarations(TY_2026_27);

        // Then
        assertThat(questions).extracting(DeclarationQuestion::code).containsExactly(
                "AGRICULTURAL_INCOME", "ASSETS_OUTSIDE_INDIA", "CASH_WITHDRAWAL_TAX_DEDUCTED", "DIRECTOR_IN_COMPANY",
                "DOUBLE_TAXATION_AGREEMENT_RELIEF", "INCOME_APPORTIONED_BETWEEN_SPOUSES", "INCOME_FROM_OUTSIDE_INDIA",
                "SIGNING_AUTHORITY_OUTSIDE_INDIA", "SPECIAL_CASE_INCOME", "START_UP_SHARE_TAX_DEFERRED",
                "TDS_ASSESSABLE_IN_ANOTHERS_HANDS", "UNILATERAL_FOREIGN_TAX_DEDUCTION", "UNLISTED_EQUITY_HELD");
        assertThat(questions).filteredOn(question -> question.answerKind().equals("AMOUNT"))
                .extracting(DeclarationQuestion::code).containsExactly("AGRICULTURAL_INCOME");
        DeclarationQuestion director = questions.get(3);
        assertThat(director.question()).isEqualTo("Were you a director in any company at any time during the tax year?");
        assertThat(director.source()).contains("r.164(3)(f)").contains("r.164(6)(f)");
    }

    @Test
    void declarations_whenTy2025_listsRule12sFourteenInCodeOrder() {
        // When — r.12(1)(a)'s proviso as in force for AY 2026-27 (W-090d, D-652)
        List<DeclarationQuestion> questions = CALCULATOR.declarations(TY_2025_26);

        // Then — r.164(3)(n)'s SPECIAL_CASE_INCOME is not this year's; (V) and (VI) are
        assertThat(questions).extracting(DeclarationQuestion::code).containsExactly(
                "AGRICULTURAL_INCOME", "ASSETS_OUTSIDE_INDIA", "CASH_WITHDRAWAL_TAX_DEDUCTED", "DIRECTOR_IN_COMPANY",
                "DOUBLE_TAXATION_AGREEMENT_RELIEF", "INCOME_APPORTIONED_BETWEEN_SPOUSES", "INCOME_FROM_OUTSIDE_INDIA",
                "INCOME_TAXABLE_UNDER_S115BBDA", "SIGNING_AUTHORITY_OUTSIDE_INDIA", "START_UP_SHARE_TAX_DEFERRED",
                "TDS_ASSESSABLE_IN_ANOTHERS_HANDS", "UNEXPLAINED_INCOME_S115BBE", "UNILATERAL_FOREIGN_TAX_DEDUCTION",
                "UNLISTED_EQUITY_HELD");
        DeclarationQuestion director = questions.get(3);
        assertThat(director.source()).contains("r.12(1)(a) proviso (IE)");
    }

    @Test
    void declarations_whenNoPackShipsForTheYear_isEmpty() {
        assertThat(CALCULATOR.declarations(TaxYearId.of(2024))).isEmpty();
    }

    @Test
    void requireAsked_whenEachAnswerIsAskedInItsKind_accepts() {
        // Given
        Map<DeclarationCode, Declaration> answers = Map.of(new DeclarationCode("DIRECTOR_IN_COMPANY"),
                new Declaration.YesNo(false), new DeclarationCode("AGRICULTURAL_INCOME"),
                new Declaration.Amount(Money.of("4000")));

        // When / Then
        assertThatCode(() -> CALCULATOR.requireAsked(TY_2026_27, answers)).doesNotThrowAnyException();
    }

    @Test
    void requireAsked_whenAnAnswerIsNotAsked_refusesNamingIt() {
        // Given
        Map<DeclarationCode, Declaration> answers = Map.of(new DeclarationCode("NOT_ASKED"), new Declaration.YesNo(true));

        // When / Then
        assertThatThrownBy(() -> CALCULATOR.requireAsked(TY_2026_27, answers))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NOT_ASKED is not a question 2026-27 asks");
    }

    @Test
    void requireAsked_whenNoPackShipsForTheYear_refusesAnyAnswerButAcceptsNone() {
        // Given
        TaxYearId unshipped = TaxYearId.of(2024);
        Map<DeclarationCode, Declaration> answers =
                Map.of(new DeclarationCode("DIRECTOR_IN_COMPANY"), new Declaration.YesNo(true));

        // When / Then
        assertThatCode(() -> CALCULATOR.requireAsked(unshipped, Map.of())).doesNotThrowAnyException();
        assertThatThrownBy(() -> CALCULATOR.requireAsked(unshipped, answers))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No questions are asked for 2024-25");
    }
}
