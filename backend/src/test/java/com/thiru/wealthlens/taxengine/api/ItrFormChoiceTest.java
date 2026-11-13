package com.thiru.wealthlens.taxengine.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.Declaration;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.FilingFacts;
import com.thiru.wealthlens.taxengine.node.ShippedNodes;
import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import com.thiru.wealthlens.taxengine.rules.filing.FieldSource;
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
import java.util.stream.Collectors;
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

    /**
     * Anand in TY 2025-26 under the old regime with an own residence bought with a loan — ₹2,50,000 of interest — and a
     * short-term capital loss of ₹30,000 carried from TY 2023-24 that his year cannot absorb (W-093a C4a).
     */
    private static EngineInputs anandWithLosses() {
        EngineInputs base = anandOldRegimeFiling(LocalDate.of(2026, 7, 31));
        com.thiru.wealthlens.taxengine.input.EnteredFacts e = base.entered();
        com.thiru.wealthlens.taxengine.input.HouseProperty home = new com.thiru.wealthlens.taxengine.input.HouseProperty(
                com.thiru.wealthlens.taxengine.input.HouseOccupancy.OWN_RESIDENCE, Money.of("480000"), Money.ZERO,
                Money.ZERO, false, Money.ZERO, com.thiru.wealthlens.taxengine.rules.money.Rate.percent("100"), Money.ZERO,
                Optional.of(LocalDate.of(2024, 6, 1)), List.of(new com.thiru.wealthlens.taxengine.input.HousingLoan(
                        com.thiru.wealthlens.taxengine.input.LoanUse.ACQUISITION_OR_CONSTRUCTION, LocalDate.of(2022, 5, 1),
                        Money.of("250000"), Money.ZERO, Money.ZERO, true, false)));
        com.thiru.wealthlens.taxengine.input.EnteredFacts withHome = new com.thiru.wealthlens.taxengine.input.EnteredFacts(
                e.employments(), e.tenancies(), e.leaveTravel(), e.allowanceUses(), e.disabledTransport(),
                e.retirementPayments(), e.accommodations(), e.medicalTreatmentAbroad(), e.vehicles(), e.meals(), e.gifts(),
                e.loans(), e.expenseBenefits(), e.assetsLent(), e.assetsTransferred(), e.householdBenefits(),
                e.childEducation(), e.shareAllotments(), e.employerContributions(), e.retirementFundHistory(),
                e.recognisedFundInterest(), e.qualifyingPayments(), e.lifeInsurancePolicies(), e.healthPayments(),
                e.disabledDependantSupport(), e.specifiedDiseaseTreatment(), e.educationLoanInterest(),
                e.purposeLoanInterest(), e.agniveerCorpusFund(), e.rentDeclaration(), e.donations(), List.of(home),
                e.otherSourceItems(), e.providentFundAccounts(), e.giftsReceived(), e.businessTrustUnits(),
                e.lifePolicyReceipts(), e.claimedLifePolicies(), e.absorptionOrder(), e.houseReinvestments());
        com.thiru.wealthlens.taxengine.input.LedgerSnapshot l = base.ledgers();
        com.thiru.wealthlens.taxengine.input.LedgerSnapshot withLoss = new com.thiru.wealthlens.taxengine.input.LedgerSnapshot(
                l.leaveTravelByBlock(), l.deductionAllowedIn(), l.retirementAllowedBefore(),
                l.housePropertyLossCarriedForward(), l.educationLoanInterestFirstYear(), Map.of(TaxYearId.of(2023),
                        new com.thiru.wealthlens.taxengine.input.CarriedCapitalLoss(Money.of("30000"), Money.ZERO, Money.ZERO,
                                Money.ZERO, true)));
        return new EngineInputs(base.taxYear(), base.regime(), base.temporal(), withHome, base.assessee(), base.family(),
                withLoss, base.elections(), base.capitalGains(), base.filing());
    }

    @Test
    void chooseForm_whenAHousePropertyLossIsSetOffAndACapitalLossCarried_fillsTheSetOffLinesByHand() {
        // When
        ItrFormChoice choice = CALCULATOR.chooseForm(anandWithLosses(), ORIGINAL_2025);

        // Then — by hand from the 2025-26 rows: salary 6,00,000 − 50,000 = 5,50,000 (SAL-STANDARD-DEDUCTION-ANY-OTHER-CASE);
        // other sources 14,000 + 90,000; the own residence's interest 2,50,000 limited to 2,00,000
        // (HP-OWN-RESIDENCE-INTEREST-CAP), a loss set off whole (SETOFF-HOUSE-PROPERTY: least of the loss and 2,00,000);
        // gross total income 4,54,000; Chapter VI-A 40,000 + 50,000; total income 3,64,000. The capital loss still to
        // carry forward bars ITR-1 (r.12(1)(a)(iv)), so ITR-2 is chosen
        assertThat(choice.form()).contains("ITR-2");
        Map<String, String> expected = Map.ofEntries(Map.entry("SALARY_INCOME", "550000"),
                Map.entry("HOUSE_PROPERTY_INCOME", "-200000"), Map.entry("HOUSE_PROPERTY_INCOME_NIL_IF_LOSS", "0"),
                Map.entry("OTHER_SOURCES_INCOME_NIL_IF_LOSS", "104000"), Map.entry("HEADS_TOTAL", "654000"),
                Map.entry("SETOFF_CURRENT_YEAR", "200000"), Map.entry("BALANCE_AFTER_SETOFF", "454000"),
                Map.entry("GROSS_TOTAL_INCOME", "454000"), Map.entry("DEDUCTIONS_VI_A", "90000"),
                Map.entry("TOTAL_INCOME", "364000"));
        expected.forEach((code, rupees) -> assertThat(cell(choice, code).amount()).as(code).contains(Money.of(rupees)));
        assertThat(verdict(choice, "ITR-1").reasons()).isNotEmpty();
        // Schedule HP's group for the one property: an own residence, annual value nil, the interest allowed 2,00,000
        assertThat(choice.cells()).filteredOn(cell -> cell.path().startsWith("HP.1[1]."))
                .extracting(ItrFormChoice.Cell::path).contains("HP.1[1].e", "HP.1[1].h", "HP.1[1].k");
        assertThat(cellAt(choice, "HP.1[1].e").amount()).contains(Money.ZERO);
        assertThat(cellAt(choice, "HP.1[1].h").amount()).contains(Money.of("200000"));
        assertThat(cellAt(choice, "HP.1[1].k").amount()).contains(Money.of("-200000"));
        assertThat(choice.cells()).noneMatch(cell -> cell.path().startsWith("HP.1[2]."));
        // Schedule OS by kind: savings 14,000 at 1b(i), deposits 90,000 at 1b(ii), 1b and 1 their sum
        Map<String, String> os = Map.of("OS.1b.i", "14000", "OS.1b.ii", "90000", "OS.1b", "104000", "OS.1", "104000",
                "OS.6", "104000");
        os.forEach((path, rupees) -> assertThat(cellAt(choice, path).amount()).as(path).contains(Money.of(rupees)));
        // Schedule CYLA (W-093a Q4a): the declared order takes salaries first (SETOFF-ACROSS-HEADS 2025-26), so the
        // 2,00,000 comes off 5,50,000 of salary; other sources stand whole. BFLA: nothing brought forward is set off,
        // so its total is 3,50,000 + 1,04,000 = 4,54,000, the gross total income
        Map<String, String> cyla = Map.ofEntries(Map.entry("CYLA.i.2", "200000"), Map.entry("CYLA.ii.1", "550000"),
                Map.entry("CYLA.ii.2", "200000"), Map.entry("CYLA.ii.4", "350000"), Map.entry("CYLA.x.1", "104000"),
                Map.entry("CYLA.x.2", "0"), Map.entry("CYLA.x.4", "104000"), Map.entry("CYLA.xiii.2", "200000"),
                Map.entry("CYLA.xiv.2", "0"), Map.entry("BFLA.xii.2", "0"), Map.entry("BFLA.xiii.3", "454000"),
                Map.entry("CFL.xi.3", "0"));
        cyla.forEach((path, rupees) -> assertThat(cellAt(choice, path).amount()).as(path).contains(Money.of(rupees)));
        // B-TI 6 reads the SET_OFF lines only: the row's MEASURE line for the amount allowed is the same 2,00,000 (D-658);
        // nothing brought forward is set off, the capital loss having no gain to absorb it
        assertThat(cell(choice, "SETOFF_BROUGHT_FORWARD").unavailableBecause()).contains("None of"
                + " [SETOFF-CARRIED-CAPITAL-GAINS, SETOFF-CARRIED-HOUSE-PROPERTY] (SET_OFF lines) applied in this run");
    }

    /** A listed equity share sold on NSE on 1 November 2025, STT paid both sides, no charges entered. */
    private static com.thiru.wealthlens.taxengine.input.CapitalGainsInput.Bucket listedEquitySold(String consideration,
            String cost, LocalDate acquired) {
        com.thiru.wealthlens.taxengine.input.CapitalGainsInput.BucketFacts facts =
                new com.thiru.wealthlens.taxengine.input.CapitalGainsInput.BucketFacts("EQUITY", "PAID", "PAID", "PURCHASE",
                        Optional.empty(), acquired, LocalDate.of(2025, 11, 1),
                        com.thiru.wealthlens.taxengine.input.CapitalGainsInput.SideVenue.UNRECORDED,
                        new com.thiru.wealthlens.taxengine.input.CapitalGainsInput.SideVenue(Optional.of("NSE"), Optional.empty()));
        return new com.thiru.wealthlens.taxengine.input.CapitalGainsInput.Bucket(Money.of(consideration), Money.of(cost),
                List.of(), facts, Optional.empty());
    }

    @Test
    void chooseForm_whenEquityIsSoldAndABroughtForwardLossAbsorbed_fillsScheduleCgByHand() {
        // Given — Priya in TY 2025-26: a short-term sale (3,00,000 for 2,40,000, held from 1-6-2025) and a long-term one
        // (5,00,000 for 3,00,000, held from 1-4-2023), with a short-term loss of 30,000 carried from TY 2023-24
        EngineInputs priya = answered(Fixtures.priya(), Map.of());
        com.thiru.wealthlens.taxengine.input.LedgerSnapshot l = priya.ledgers();
        EngineInputs selling = new EngineInputs(TY_2025_26, priya.regime(),
                TemporalContext.forTaxYear(TY_2025_26, LocalDate.of(2025, 12, 1)), priya.entered(), priya.assessee(),
                priya.family(), new com.thiru.wealthlens.taxengine.input.LedgerSnapshot(l.leaveTravelByBlock(),
                        l.deductionAllowedIn(), l.retirementAllowedBefore(), l.housePropertyLossCarriedForward(),
                        l.educationLoanInterestFirstYear(), Map.of(TaxYearId.of(2023),
                                new com.thiru.wealthlens.taxengine.input.CarriedCapitalLoss(Money.of("30000"), Money.ZERO,
                                        Money.ZERO, Money.ZERO, true))),
                priya.elections(), com.thiru.wealthlens.taxengine.test.CapitalGainsHarness.inputOf(
                        listedEquitySold("300000", "240000", LocalDate.of(2025, 6, 1)),
                        listedEquitySold("500000", "300000", LocalDate.of(2023, 4, 1))),
                priya.filing());

        // When
        ItrFormChoice choice = CALCULATOR.chooseForm(answered(selling, Map.of()), ORIGINAL_2025);

        // Then — a short-term gain bars ITR-1; A2: 3,00,000 − 2,40,000 = 60,000; B3 (s.112A): 5,00,000 − 3,00,000 =
        // 2,00,000; A9 60,000, B12 2,00,000, C1 2,60,000; the carried short-term loss set off against the short-term
        // gain (s.74): B-TI 8 is 30,000
        assertThat(choice.form()).contains("ITR-2");
        Map<String, String> expected = Map.ofEntries(Map.entry("CG.A2.a", "300000"), Map.entry("CG.A2.b.i", "240000"),
                Map.entry("CG.A2.b.iii", "0"), Map.entry("CG.A2.b.iv", "240000"), Map.entry("CG.A2.c", "60000"),
                Map.entry("CG.A2.e", "60000"), Map.entry("CG.A9", "60000"), Map.entry("CG.B3.a", "200000"),
                Map.entry("CG.B3.c", "200000"), Map.entry("CG.B12", "200000"), Map.entry("CG.C1", "260000"),
                Map.entry("CG.A5.a.iii", "0"), Map.entry("CG.B8.e", "0"), Map.entry("B-TI.8", "30000"));
        expected.forEach((path, rupees) -> assertThat(cellAt(choice, path).amount()).as(path).contains(Money.of(rupees)));
        assertThat(cellAt(choice, "CG.A2.b.ii").unavailableBecause()).isPresent();
        // Schedule BFLA: the carried short-term loss of 30,000 comes off the 20% short-term row (s.74)
        Map<String, String> bfla = Map.of("CYLA.iv.1", "60000", "CYLA.iv.4", "60000", "CYLA.viii.1", "200000",
                "BFLA.iii.1", "60000", "BFLA.iii.2", "30000", "BFLA.iii.3", "30000", "BFLA.vii.3", "200000",
                "BFLA.xii.2", "30000");
        bfla.forEach((path, rupees) -> assertThat(cellAt(choice, path).amount()).as(path).contains(Money.of(rupees)));
    }

    @Test
    void chooseForm_whenTheRegimeIsNotTheYears_refusesNamingIt() {
        // Given
        EngineInputs priya = answered(Fixtures.priya(), Map.of());
        EngineInputs unknown = new EngineInputs(priya.taxYear(), new RegimeCode("SYN_NO_SUCH_REGIME"), priya.temporal(),
                priya.entered(), priya.assessee(), priya.family(), priya.ledgers(), priya.elections(), priya.capitalGains(),
                priya.filing());

        // When / Then
        assertThatThrownBy(() -> CALCULATOR.chooseForm(unknown, ORIGINAL_2026))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The tax year has no regime SYN_NO_SUCH_REGIME");
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
        ItrFormChoice choice = ItrFormChoice.of(TY_2026_27, new RegimeCode("NEW_TAX_REGIME"), ORIGINAL_2026, none, forms,
                List.of());

        // Then
        assertThat(choice.form()).isEmpty();
        assertThat(choice.message()).contains("No form shipped for 2026-27 serves this return; each form's reasons are"
                + " listed");
        assertThat(choice.verdicts()).singleElement().satisfies(verdict -> assertThat(verdict.reasons())
                .containsExactly("closed"));
    }
    /** A persona's inputs moved to TY 2025-26, the year whose forms carry schedules (W-092). */
    private static EngineInputs inTy2025(EngineInputs persona) {
        return new EngineInputs(TY_2025_26, persona.regime(),
                TemporalContext.forTaxYear(TY_2025_26, LocalDate.of(2025, 9, 21)), persona.entered(), persona.assessee(),
                persona.family(), persona.ledgers(), persona.elections(), persona.capitalGains(), persona.filing());
    }

    private static ItrFormChoice.Cell cellAt(ItrFormChoice choice, String path) {
        return choice.cells().stream().filter(cell -> cell.path().equals(path)).findFirst().orElseThrow();
    }

    private static ItrFormChoice.Cell cell(ItrFormChoice choice, String code) {
        return choice.cells().stream().filter(cell -> cell.cell().equals(code)).findFirst().orElseThrow();
    }

    @Test
    void chooseForm_whenAnandFilesTy2025_fillsItr1sCellsWithTheHandDerivedFigures() {
        // When — Anand: pension ₹6,00,000, interest ₹14,000 + ₹90,000, new regime, TY 2025-26
        ItrFormChoice choice = CALCULATOR.chooseForm(answered(inTy2025(Fixtures.anand()), Map.of()), ORIGINAL_2025);

        // Then — by hand from the 2025-26 rows: SAL-STANDARD-DEDUCTION-NEW-TAX-REGIME ₹75,000; RATE-SLAB-NEW-TAX-REGIME
        // nil to ₹4,00,000 then 5% → (6,29,000 − 4,00,000) × 5% = 11,450; REBATE-NEW-TAX-REGIME (ceiling ₹12,00,000,
        // limited to slab tax) takes all of it
        assertThat(choice.form()).contains("ITR-1");
        Map<String, String> expected = Map.ofEntries(Map.entry("SALARY_GROSS", "600000"),
                Map.entry("SALARY_EXEMPT_U10", "0"), Map.entry("SALARY_NET", "600000"),
                Map.entry("SALARY_DEDUCTIONS_16", "75000"), Map.entry("SALARY_STANDARD_DEDUCTION", "75000"),
                Map.entry("SALARY_INCOME", "525000"), Map.entry("OTHER_SOURCES_INCOME", "104000"),
                Map.entry("GROSS_TOTAL_INCOME", "629000"), Map.entry("TAX_REBATE_87A", "11450"),
                Map.entry("TAX_CESS", "0"), Map.entry("AMOUNT_PAYABLE", "0"), Map.entry("REFUND", "0"));
        expected.forEach((code, rupees) -> assertThat(cell(choice, code).amount()).as(code).contains(Money.of(rupees)));
        assertThat(cell(choice, "SALARY_GROSS").path()).isEqualTo("B1.i");
        assertThat(cell(choice, "SALARY_17_1").unavailableBecause()).hasValueSatisfying(reason -> assertThat(reason)
                .startsWith("Split gross salary between section 17(1) and section 17(3)"));
        assertThat(cell(choice, "SALARY_PROFESSIONAL_TAX").unavailableBecause())
                .contains("None of [SAL-TAX-ON-EMPLOYMENT] applied in this run");
    }

    @Test
    void chooseForm_whenOnePersonFilesEitherForm_showsEachSalaryCellAlikeAtEachFormsPath() {
        // Given — T21-14: Anand as he is (ITR-1), and as a director (ITR-2); the salary figures are the same
        ItrFormChoice itr1 = CALCULATOR.chooseForm(answered(inTy2025(Fixtures.anand()), Map.of()), ORIGINAL_2025);
        ItrFormChoice itr2 = CALCULATOR.chooseForm(answered(inTy2025(Fixtures.anand()),
                Map.of("DIRECTOR_IN_COMPANY", new Declaration.YesNo(true))), ORIGINAL_2025);

        // Then
        assertThat(itr2.form()).contains("ITR-2");
        for (String code : List.of("SALARY_GROSS", "SALARY_EXEMPT_U10", "SALARY_NET", "SALARY_DEDUCTIONS_16",
                "SALARY_STANDARD_DEDUCTION", "SALARY_INCOME")) {
            assertThat(cell(itr2, code).amount()).as(code).isEqualTo(cell(itr1, code).amount());
            assertThat(cell(itr2, code).label()).isEqualTo(cell(itr1, code).label());
        }
        assertThat(cell(itr1, "SALARY_GROSS").path()).isEqualTo("B1.i");
        assertThat(cell(itr2, "SALARY_GROSS").path()).isEqualTo("S.2");
        assertThat(cell(itr2, "SALARY_INCOME").schedule()).isEqualTo("S");
    }

    @Test
    void schedules_whenShipped_readOnlyNodesThePipelineProduces() {
        // Given — I21's node half, which a pack cannot check (it holds no node registry)
        Set<String> produced = ShippedNodes.all().stream().map(ComputationNode::produces).map(Object::toString)
                .collect(Collectors.toSet());
        var forms = ShippedRulePacks.compile("rulepacks", Set.of(TY_2025_26)).load(TY_2025_26).itrForms().values();

        // When
        List<String> read = forms.stream().flatMap(form -> form.schedules().stream())
                .flatMap(schedule -> schedule.fields().stream()).map(field -> field.cell().source())
                .filter(FieldSource.FromNodes.class::isInstance).map(FieldSource.FromNodes.class::cast)
                .flatMap(source -> source.nodes().stream()).map(Object::toString).distinct().toList();

        // Then
        assertThat(read).isNotEmpty();
        assertThat(produced).containsAll(read);
    }

    @Test
    void schedules_whenShipped_readOnlyKindsAndItemFiguresTheEngineReports() {
        // Given — the kinds OtherSourcesHead reports, and the figures each house property carries (D-658)
        Set<String> kinds = new java.util.HashSet<>(java.util.Arrays.stream(
                com.thiru.wealthlens.taxengine.input.OtherSourceKind.values()).map(Enum::name).toList());
        kinds.addAll(Set.of("PROVIDENT_FUND_INTEREST", "GIFTS", "BUSINESS_TRUST", "LIFE_POLICY"));
        Set<String> figures = java.util.Arrays.stream(
                com.thiru.wealthlens.taxengine.pipeline.HousePropertyHead.PropertyFigures.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).filter(name -> !name.equals("position"))
                .collect(Collectors.toSet());
        var forms = ShippedRulePacks.compile("rulepacks", Set.of(TY_2025_26)).load(TY_2025_26).itrForms().values();
        List<FieldSource> sources = forms.stream().flatMap(form -> form.schedules().stream())
                .flatMap(schedule -> java.util.stream.Stream.concat(schedule.fields().stream(),
                        schedule.groups().stream().flatMap(group -> group.fields().stream())))
                .map(field -> field.cell().source()).toList();

        // When / Then
        assertThat(sources.stream().filter(FieldSource.FromOtherSourceKinds.class::isInstance)
                .map(FieldSource.FromOtherSourceKinds.class::cast).flatMap(source -> source.kinds().stream()))
                .isNotEmpty().allMatch(kinds::contains);
        assertThat(sources.stream().filter(FieldSource.FromItemFigure.class::isInstance)
                .map(FieldSource.FromItemFigure.class::cast).map(FieldSource.FromItemFigure::figure))
                .isNotEmpty().allMatch(figures::contains);
    }
    /** Anand in TY 2025-26 under the old regime, filing on {@code furnishedOn} for a due date of 31 July 2026. */
    private static EngineInputs anandOldRegimeFiling(LocalDate furnishedOn) {
        EngineInputs moved = answered(inTy2025(Fixtures.anand()), Map.of());
        FilingFacts filed = moved.filing();
        return new EngineInputs(moved.taxYear(), new RegimeCode("FIRST_SCHEDULE_RATES"), moved.temporal(), moved.entered(),
                moved.assessee(), moved.family(), moved.ledgers(), moved.elections(), moved.capitalGains(),
                new FilingFacts(LocalDate.of(2026, 7, 31), Optional.of(furnishedOn), List.of(), Money.ZERO, List.of(),
                        filed.declarations()));
    }

    @Test
    void chooseForm_whenAnandFilesTy2025_fillsItr1sPartDByHand() {
        // When — new regime: slab tax ₹11,450, all rebated (as above); nothing paid, nothing late
        ItrFormChoice choice = CALCULATOR.chooseForm(answered(inTy2025(Fixtures.anand()), Map.of()), ORIGINAL_2025);

        // Then — D1 is slab plus special rates (none), D3 = D1 − D2, D5 = D3 + D4, D11 = D5 + D7 + D8 + D9 + D10
        Map<String, String> expected = Map.of("TAX_ON_TOTAL_INCOME", "11450", "TAX_AFTER_REBATE", "0",
                "TAX_LIABILITY", "0", "TOTAL_LIABILITY", "0", "AMOUNT_PAYABLE", "0", "REFUND", "0");
        expected.forEach((code, rupees) -> assertThat(cell(choice, code).amount()).as(code).contains(Money.of(rupees)));
        assertThat(cell(choice, "TAX_ON_TOTAL_INCOME").path()).isEqualTo("D1");
        assertThat(cell(choice, "RELIEF_89").unavailableBecause()).isPresent();
    }

    @Test
    void chooseForm_whenAnandClaimsUnderTheOldRegime_placesEachSectionsDeductionAndTheyMakeTheTotal() {
        // When — the probe's lines: 80D ₹40,000 (DED-HEALTH-FAMILY-SENIOR), 80TTB ₹1,04,000 capped at ₹50,000
        ItrFormChoice choice = CALCULATOR.chooseForm(anandOldRegimeFiling(LocalDate.of(2026, 7, 31)), ORIGINAL_2025);

        // Then
        assertThat(choice.form()).contains("ITR-1");
        assertThat(cell(choice, "DED_80D").amount()).contains(Money.of("40000"));
        assertThat(cell(choice, "DED_80TTB").amount()).contains(Money.of("50000"));
        assertThat(cell(choice, "DED_80D").path()).isEqualTo("C.80D");
        Money sections = choice.cells().stream().filter(cell -> cell.cell().startsWith("DED_"))
                .flatMap(cell -> cell.amount().stream()).reduce(Money.ZERO, Money::plus);
        assertThat(sections).isEqualTo(Money.of("90000"));
        assertThat(cell(choice, "DEDUCTIONS_VI_A").amount()).contains(Money.of("90000"));
        assertThat(cell(choice, "DED_80CCC").unavailableBecause()).hasValueSatisfying(reason -> assertThat(reason)
                .startsWith("Included under 80C"));
    }

    @Test
    void chooseForm_whenTheReturnIsLate_carriesInterestAndTheFeeIntoTheAmountPayable() {
        // When — filed 15 September 2026, after the 31 July due date, with nothing paid; round.netpayable rounds the
        // balance with interest and fees (RoundNetPayableNode), so the payable must agree with D11
        ItrFormChoice choice = CALCULATOR.chooseForm(anandOldRegimeFiling(LocalDate.of(2026, 9, 15)), ORIGINAL_2025);

        // Then — FEE-LATE-RETURN 2025-26: ₹5,000 where total income exceeds ₹5,00,000 (Anand's is ₹5,64,000)
        assertThat(cell(choice, "FEE_234F").amount()).contains(Money.of("5000"));
        Money tax = cell(choice, "TAX_LIABILITY").amount().orElseThrow();
        Money interestAndFee = java.util.stream.Stream.of("INTEREST_234A", "INTEREST_234B", "INTEREST_234C", "FEE_234F")
                .map(code -> cell(choice, code).amount().orElseThrow()).reduce(Money.ZERO, Money::plus);
        assertThat(cell(choice, "TOTAL_LIABILITY").amount()).contains(tax.plus(interestAndFee));
        // s.288B: to the nearest ₹10, five rupees or more rounding up
        long owed = tax.plus(interestAndFee).toString().contains(".")
                ? new java.math.BigDecimal(tax.plus(interestAndFee).toString()).longValue()
                : Long.parseLong(tax.plus(interestAndFee).toString());
        long rounded = owed % 10 >= 5 ? owed - owed % 10 + 10 : owed - owed % 10;
        assertThat(cell(choice, "AMOUNT_PAYABLE").amount()).contains(Money.of(Long.toString(rounded)));
        assertThat(rounded).isGreaterThan(Long.parseLong(new java.math.BigDecimal(tax.toString()).setScale(0,
                java.math.RoundingMode.DOWN).toPlainString()) + 5000 - 10);
        assertThat(cell(choice, "REFUND").amount()).contains(Money.ZERO);
    }
}
