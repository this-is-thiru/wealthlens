package com.thiru.wealthlens.taxengine.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.Declaration;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.FilingFacts;
import com.thiru.wealthlens.taxengine.rules.filing.FormVerdict;
import com.thiru.wealthlens.taxengine.rules.filing.ItrSelection;
import com.thiru.wealthlens.taxengine.rules.id.DeclarationCode;
import com.thiru.wealthlens.taxengine.rules.id.ItrFormCode;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.ReturnKind;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import com.thiru.wealthlens.taxengine.test.Fixtures;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The form chosen for a computed return on the shipped packs (DR 21 I4, I8; W-091, D-653). The personas are DR 04's;
 * each answers every question of the year "no" (and nil agricultural income) unless a case says otherwise. The limits
 * themselves — ₹1,25,000, ₹5,000, ₹50 lakh, eighty years — are proven a rupee either side in {@code ItrForms2026Test}
 * and {@code ItrForms2025Test}; here the whole path from inputs to verdicts is.
 */
class ItrFormChoiceTest {

    private static final TaxYearId TY_2025_26 = TaxYearId.of(2025);
    private static final TaxYearId TY_2026_27 = TaxYearId.of(2026);
    private static final TaxCalculator CALCULATOR =
            new TaxCalculator(ShippedRulePacks.compile("rulepacks", Set.of(TY_2025_26, TY_2026_27)));
    private static final ReturnKind ORIGINAL_2026 = new ReturnKind("S263_1");
    private static final ReturnKind ORIGINAL_2025 = new ReturnKind("S139_1");

    /** The inputs with every question of their year answered: "no", nil, or as {@code overrides} says. */
    private static EngineInputs answered(EngineInputs inputs, Map<String, Declaration> overrides) {
        Map<DeclarationCode, Declaration> answers = new HashMap<>();
        CALCULATOR.declarations(inputs.taxYear()).forEach(question -> answers.put(new DeclarationCode(question.code()),
                overrides.getOrDefault(question.code(), "AMOUNT".equals(question.answerKind())
                        ? new Declaration.Amount(Money.ZERO) : new Declaration.YesNo(false))));
        return withAnswers(inputs, answers);
    }

    private static EngineInputs withAnswers(EngineInputs inputs, Map<DeclarationCode, Declaration> answers) {
        FilingFacts filed = inputs.filing();
        return new EngineInputs(inputs.taxYear(), inputs.regime(), inputs.temporal(), inputs.entered(),
                inputs.assessee(), inputs.family(), inputs.ledgers(), inputs.elections(), inputs.capitalGains(),
                new FilingFacts(filed.dueDate(), filed.furnishedOn(), filed.advanceTaxPaid(), filed.taxAtSource(),
                        filed.selfAssessmentPaid(), answers));
    }

    private static ItrFormChoice.Verdict verdict(ItrFormChoice choice, String form) {
        return choice.verdicts().stream().filter(verdict -> verdict.form().equals(form)).findFirst().orElseThrow();
    }

    @Test
    void chooseForm_whenAPensionerHasInterestOnly_choosesItr1AndExcludesItr2() {
        // When — Anand: pension ₹6,00,000, interest ₹1,04,000, 62
        ItrFormChoice choice = CALCULATOR.chooseForm(answered(Fixtures.anand(), Map.of()), ORIGINAL_2026);

        // Then — T21-1: no bar triggered; ITR-2 excluded by r.164(4)
        assertThat(choice.form()).contains("ITR-1");
        assertThat(choice.message()).isEmpty();
        assertThat(verdict(choice, "ITR-1").reasons()).isEmpty();
        assertThat(verdict(choice, "ITR-1").displayName()).isEqualTo("SAHAJ (ITR-1)");
        assertThat(verdict(choice, "ITR-2").standing()).isEqualTo("EXCLUDED");
        assertThat(verdict(choice, "ITR-2").excludedBy()).contains("ITR-1");
        assertThat(choice.furnishing()).containsExactly("DIGITAL_SIGNATURE", "ELECTRONIC_VERIFICATION_CODE",
                "ELECTRONIC_WITH_ITR_V");
        assertThat(choice.taxYear()).isEqualTo("2026-27");
        assertThat(choice.returnKind()).isEqualTo("S263_1");
    }

    @Test
    void chooseForm_whenGainsAreShortTermAndLongTermOverTheLimit_choosesItr2NamingBoth() {
        // When — Ravi: long-term listed equity ₹1,50,000 and short-term ₹40,000
        ItrFormChoice choice = CALCULATOR.chooseForm(answered(Fixtures.ravi(), Map.of()), ORIGINAL_2026);

        // Then — T21-3, T21-4: r.164(2)(d) twice
        assertThat(choice.form()).contains("ITR-2");
        ItrFormChoice.Verdict itr1 = verdict(choice, "ITR-1");
        assertThat(itr1.standing()).isEqualTo("INELIGIBLE");
        assertThat(itr1.reasons()).hasSize(2);
        assertThat(verdict(choice, "ITR-2").standing()).isEqualTo("ELIGIBLE");
    }

    @Test
    void chooseForm_whenTotalIncomeExceedsFiftyLakh_namesTheBar() {
        // When — Meera: total income above ₹50,00,000, long-term gain ₹5,50,000
        ItrFormChoice choice = CALCULATOR.chooseForm(answered(Fixtures.meera(), Map.of()), ORIGINAL_2026);

        // Then
        assertThat(choice.form()).contains("ITR-2");
        assertThat(verdict(choice, "ITR-1").reasons())
                .contains("r.164(3)(k): has total income exceeding fifty lakh rupees");
    }

    @Test
    void chooseForm_whenTheSalariedPersonIsADirector_choosesItr2NamingTheBar() {
        // When — Priya, salary only, answering yes to r.164(3)(f)
        ItrFormChoice choice = CALCULATOR.chooseForm(
                answered(Fixtures.priya(), Map.of("DIRECTOR_IN_COMPANY", new Declaration.YesNo(true))), ORIGINAL_2026);

        // Then — T21-5's shape: one declaration closes ITR-1
        assertThat(choice.form()).contains("ITR-2");
        assertThat(verdict(choice, "ITR-1").reasons()).containsExactly("r.164(3)(f): is a director in any company");
    }

    @Test
    void chooseForm_whenTheYearsQuestionsAreUnanswered_choosesNoneAndAsksThem() {
        // When — C3a: Priya answers nothing
        ItrFormChoice choice = CALCULATOR.chooseForm(Fixtures.priya(), ORIGINAL_2026);

        // Then
        assertThat(choice.form()).isEmpty();
        assertThat(choice.furnishing()).isEmpty();
        assertThat(verdict(choice, "ITR-1").standing()).isEqualTo("UNDETERMINED");
        assertThat(verdict(choice, "ITR-1").unanswered()).contains("DIRECTOR_IN_COMPANY", "AGRICULTURAL_INCOME");
        assertThat(choice.message()).hasValueSatisfying(message -> assertThat(message)
                .startsWith("Answer AGRICULTURAL_INCOME, ASSETS_OUTSIDE_INDIA")
                .endsWith("to settle whether ITR-1 serves this return"));
    }

    @Test
    void chooseForm_whenTheReturnIsForTy2025_usesThatYearsForms() {
        // Given — T21-11: Priya's pay in TY 2025-26, under r.12 of the 1962 Rules (W-090d)
        EngineInputs priya = Fixtures.priya();
        EngineInputs earlier = new EngineInputs(TY_2025_26, priya.regime(),
                TemporalContext.forTaxYear(TY_2025_26, LocalDate.of(2025, 9, 21)), priya.entered(), priya.assessee(),
                priya.family(), priya.ledgers(), priya.elections(), priya.capitalGains(), priya.filing());

        // When
        ItrFormChoice choice = CALCULATOR.chooseForm(answered(earlier, Map.of()), ORIGINAL_2025);

        // Then
        assertThat(choice.form()).contains("ITR-1");
        assertThat(choice.taxYear()).isEqualTo("2025-26");
        assertThatThrownBy(() -> CALCULATOR.chooseForm(answered(earlier, Map.of()), ORIGINAL_2026))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No form for 2025-26 serves the return kind S263_1; the year's kinds are"
                        + " [S139_1, S142_1_I, S148]");
    }

    @Test
    void of_whenNoFormIsOpenAndNoneWaits_saysNoShippedFormServes() {
        // Given — C4a: both forms closed, as for business income, which the engine does not yet compute
        ItrSelection none = new ItrSelection(List.of(
                new FormVerdict(new ItrFormCode("ITR-1"), FormVerdict.Standing.INELIGIBLE, List.of("closed"), List.of(),
                        Optional.empty(), Set.of())), Optional.empty(), List.of());
        var forms = ShippedRulePacks.compile("rulepacks", Set.of(TY_2026_27))
                .load(TY_2026_27).itrForms();

        // When
        ItrFormChoice choice = ItrFormChoice.of(TY_2026_27, new RegimeCode("NEW_TAX_REGIME"), ORIGINAL_2026, none, forms);

        // Then
        assertThat(choice.form()).isEmpty();
        assertThat(choice.message()).contains("No form shipped for 2026-27 serves this return; each form's reasons are"
                + " listed");
        assertThat(choice.verdicts()).singleElement().satisfies(verdict -> assertThat(verdict.reasons())
                .containsExactly("closed"));
    }
}
