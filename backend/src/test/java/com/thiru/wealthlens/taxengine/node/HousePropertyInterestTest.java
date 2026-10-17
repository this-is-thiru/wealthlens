package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.HouseOccupancy;
import com.thiru.wealthlens.taxengine.input.HouseProperty;
import com.thiru.wealthlens.taxengine.input.HousingLoan;
import com.thiru.wealthlens.taxengine.input.LoanPurpose;
import com.thiru.wealthlens.taxengine.input.LoanUse;
import com.thiru.wealthlens.taxengine.input.PurposeLoanInterest;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.test.LockedInstruments;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Interest under income from house property (S2 s.22(1)(b), (c), (2)–(6) with FA 2026 s.38; 1961 s.24(b), s.25; D-156).
 * Figures by hand from {@code DR 01} R16's worked example: interest ₹3,20,000 and pre-construction interest ₹2,00,000 in
 * its third instalment, ₹40,000. Where an own residence's interest is tested, R16's let house — ₹5,40,000 rent, ₹18,000
 * local taxes, so ₹5,22,000 less thirty per cent, ₹3,65,400 — stands beside it, because the head below nil is refused
 * until set-off (W-081).
 */
class HousePropertyInterestTest {

    private static final RegimeCode OLD = new RegimeCode("FIRST_SCHEDULE_RATES");
    private static final RegimeCode NEW = new RegimeCode("NEW_TAX_REGIME");
    private static final Rate WHOLE = Rate.percent("100");

    /** Borrowed in TY 2022-23, so completion by 31-3-2028 earns the higher limit; acquired in TY 2024-25. */
    private static final LocalDate BORROWED = LocalDate.of(2022, 5, 1);
    private static final LocalDate ACQUIRED = LocalDate.of(2024, 6, 1);

    private static HousingLoan loan(String interest, String preConstruction, boolean certificate) {
        return new HousingLoan(LoanUse.ACQUISITION_OR_CONSTRUCTION, BORROWED, Money.of(interest),
                Money.of(preConstruction), Money.ZERO, certificate, false);
    }

    private static final HousingLoan R16_LOAN = loan("320000", "200000", true);

    private static HouseProperty let(LocalDate acquired, HousingLoan... loans) {
        return new HouseProperty(HouseOccupancy.LET, Money.of("480000"), Money.of("540000"), Money.ZERO, false,
                Money.of("18000"), WHOLE, Money.ZERO, Optional.of(acquired), List.of(loans));
    }

    private static HouseProperty home(LocalDate acquired, HousingLoan... loans) {
        return new HouseProperty(HouseOccupancy.OWN_RESIDENCE, Money.of("480000"), Money.ZERO, Money.ZERO, false, Money.ZERO,
                WHOLE, Money.ZERO, Optional.of(acquired), List.of(loans));
    }

    /** R16's let house without a loan: ₹3,65,400. */
    private static final HouseProperty R16_LET = let(ACQUIRED);

    private static NodeResult run(int year, RegimeCode regime, List<HouseProperty> properties) {
        return run(year, regime, properties, List.of());
    }

    private static NodeResult run(int year, RegimeCode regime, List<HouseProperty> properties,
            List<PurposeLoanInterest> purposeLoans) {
        TaxYearId taxYear = TaxYearId.of(year);
        RulePack pack = new ClasspathRulePackProvider(
                        "rulepacks", Set.of(taxYear), new JsonPackAssembler(LockedInstruments.of(taxYear)))
                .load(taxYear);
        EngineInputs plain = NodeHarness.plainTaxpayer();
        EngineInputs inputs = new EngineInputs(taxYear, regime,
                TemporalContext.forTaxYear(taxYear, LocalDate.of(year, 9, 21)),
                new EnteredFacts(List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of(), purposeLoans, Optional.empty(), Optional.empty(),
                        List.of(), properties, List.of(), List.of(), List.of(), List.of(), List.of()),
                plain.assessee(), plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(),
                plain.filing());
        return NodeHarness.with(pack).regime(regime).inputs(inputs).run(new HousePropertyNode());
    }

    private static MoneyValue rupees(String amount) {
        return MoneyValue.of(Money.of(amount));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenR16sLetHouseHasItsLoan_deductsTheInterestAndTheThirdInstalmentWhole(int year) {
        // When
        NodeResult result = run(year, OLD, List.of(let(ACQUIRED, R16_LOAN)));

        // Then — 5,22,000 − 1,56,600 − 3,20,000 − 2,00,000 ÷ 5 = 5,400 (R16's figure; the second and third instalments)
        assertThat(result.value()).isEqualTo(rupees("5400"));
        assertThat(result.trace()).extracting(TraceLine::rule).contains(RuleIds.HP_INTEREST,
                RuleIds.HP_PRE_CONSTRUCTION);
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenTheOwnResidenceQualifies_limitsItsInterestAndInstalmentToTheHigherFigure(int year) {
        // When
        NodeResult result = run(year, OLD, List.of(R16_LET, home(ACQUIRED, R16_LOAN)));

        // Then — 3,20,000 + 40,000 = 3,60,000 within 2,00,000 (s.22(2)(a) with FA 2026 s.38; 1961 second proviso
        // and Explanation): 3,65,400 − 2,00,000
        assertThat(result.value()).isEqualTo(rupees("165400"));
        assertThat(result.trace()).last().satisfies(line -> {
            assertThat(line.rule()).isEqualTo(RuleIds.HP_OWN_RESIDENCE_INTEREST_CAP);
            assertThat(line.in()).isEqualTo(rupees("360000"));
            assertThat(line.out()).isEqualTo(rupees("200000"));
        });
    }

    @Test
    void evaluate_whenNoLenderCertificateIsFurnished_limitsTheOwnResidenceToTheLowerFigure() {
        // When
        NodeResult result = run(2026, OLD, List.of(R16_LET, home(ACQUIRED, loan("320000", "200000", false))));

        // Then — s.22(2)(b): 30,000; 3,65,400 − 30,000
        assertThat(result.value()).isEqualTo(rupees("335400"));
    }

    @Test
    void evaluate_whenTheLoanWasForRepairs_limitsTheOwnResidenceToTheLowerFigure() {
        // When — repairs, certificate furnished
        HousingLoan repairs = new HousingLoan(LoanUse.REPAIR_OR_RECONSTRUCTION, BORROWED, Money.of("50000"),
                Money.ZERO, Money.ZERO, true, false);
        NodeResult result = run(2026, OLD, List.of(R16_LET, home(ACQUIRED, repairs)));

        // Then — s.22(2)(a)(i) needs acquisition or construction: 30,000
        assertThat(result.value()).isEqualTo(rupees("335400"));
    }

    @Test
    void evaluate_whenOneOwnResidenceQualifiesAndOneDoesNot_limitsTheSecondThenBoth() {
        // When — the qualifying house 1,00,000, the other 50,000
        NodeResult result = run(2026, OLD, List.of(R16_LET, home(ACQUIRED, loan("100000", "0", true)),
                home(ACQUIRED, loan("50000", "0", false))));

        // Then — 1,00,000 + min(50,000, 30,000) = 1,30,000, under 2,00,000: 3,65,400 − 1,30,000
        assertThat(result.value()).isEqualTo(rupees("235400"));
    }

    @Test
    void evaluate_whenTwoOwnResidencesTogetherExceedTheAggregate_limitsThemToIt() {
        // When — the qualifying house 1,90,000, the other 50,000
        NodeResult result = run(2026, OLD, List.of(R16_LET, home(ACQUIRED, loan("190000", "0", true)),
                home(ACQUIRED, loan("50000", "0", false))));

        // Then — 1,90,000 + 30,000 = 2,20,000 within s.22(5)'s 2,00,000: 3,65,400 − 2,00,000
        assertThat(result.value()).isEqualTo(rupees("165400"));
    }

    @Test
    void evaluate_whenTheNewRegimeAppliesInTaxYear2026_allowsOnlyTheOwnResidencesInstalment() {
        // When
        NodeResult result = run(2026, NEW, List.of(R16_LET, home(ACQUIRED, R16_LOAN)));

        // Then — s.202(2)(a)(v) removes s.22(1)(b) only (D-156 C1(a)): 3,65,400 − 40,000
        assertThat(result.value()).isEqualTo(rupees("325400"));
        assertThat(result.trace()).filteredOn(line -> line.rule().equals(RuleIds.HP_OWN_RESIDENCE_INTEREST))
                .singleElement()
                .satisfies(line -> assertThat(line.out()).isEqualTo(MoneyValue.of(Money.ZERO)));
    }

    @Test
    void evaluate_whenTheNewRegimeAppliesInTaxYear2025_allowsNothingForTheOwnResidence() {
        // When
        NodeResult result = run(2025, NEW, List.of(R16_LET, home(ACQUIRED, R16_LOAN)));

        // Then — 1961 s.115BAC(2)(i) removes the whole of s.24(b), the Explanation within it
        assertThat(result.value()).isEqualTo(rupees("365400"));
    }

    @Test
    void evaluate_whenALetHouseIsInTheNewRegime_deductsItsInterestWhole() {
        // When
        NodeResult result = run(2026, NEW, List.of(let(ACQUIRED, R16_LOAN)));

        // Then — only s.21(6) properties lose s.22(1)(b): R16's 5,400
        assertThat(result.value()).isEqualTo(rupees("5400"));
    }

    @Test
    void evaluate_whenCapitalWasBorrowedBeforeApril1999_limitsTaxYear2025ToTheLowerFigureOnly() {
        // Given — borrowed 1-6-1998, completed 1-6-2001, within five years of 31-3-1999; certificate furnished
        HousingLoan old = new HousingLoan(LoanUse.ACQUISITION_OR_CONSTRUCTION, LocalDate.of(1998, 6, 1),
                Money.of("100000"), Money.ZERO, Money.ZERO, true, false);
        List<HouseProperty> properties = List.of(R16_LET, home(LocalDate.of(2001, 6, 1), old));

        // When
        NodeResult taxYear2025 = run(2025, OLD, properties);
        NodeResult taxYear2026 = run(2026, OLD, properties);

        // Then — 1961 second proviso "on or after the 1st day of April, 1999": 30,000; S2 s.22(2)(a) has no date
        assertThat(taxYear2025.value()).isEqualTo(rupees("335400"));
        assertThat(taxYear2026.value()).isEqualTo(rupees("265400"));
    }

    @Test
    void evaluate_whenCompletionFallsOnTheLastDayOfTheFiveYears_earnsTheHigherLimit() {
        // Given — borrowed 1-6-2019 (TY 2019-20, ending 31-3-2020); five years later is 31-3-2025
        HousingLoan borrowed2019 = new HousingLoan(LoanUse.ACQUISITION_OR_CONSTRUCTION, LocalDate.of(2019, 6, 1),
                Money.of("100000"), Money.ZERO, Money.ZERO, true, false);

        // When
        NodeResult inTime = run(2026, OLD, List.of(R16_LET, home(LocalDate.of(2025, 3, 31), borrowed2019)));
        NodeResult late = run(2026, OLD, List.of(R16_LET, home(LocalDate.of(2025, 4, 1), borrowed2019)));

        // Then — 3,65,400 − 1,00,000; a day late, 3,65,400 − 30,000
        assertThat(inTime.value()).isEqualTo(rupees("265400"));
        assertThat(late.value()).isEqualTo(rupees("335400"));
    }

    @Test
    void evaluate_whenTheTaxYearIsTheFifthAfterAcquisition_allowsTheLastInstalmentAndNoneAfter() {
        // Given — 2,00,000 of pre-construction interest, no interest for the year
        HousingLoan prior = loan("0", "200000", true);

        // When — acquired in TY 2022-23 (TY 2026-27 is the fifth instalment) and in TY 2021-22 (none left)
        NodeResult fifth = run(2026, OLD, List.of(let(LocalDate.of(2022, 6, 1), prior)));
        NodeResult sixth = run(2026, OLD, List.of(let(LocalDate.of(2021, 6, 1), prior)));

        // Then — 3,65,400 − 40,000; 3,65,400
        assertThat(fifth.value()).isEqualTo(rupees("325400"));
        assertThat(sixth.value()).isEqualTo(rupees("365400"));
    }

    @Test
    void evaluate_whenPartOfTheInterestIsDeductedUnderSection131_deductsOnlyTheRestUnderTheHead() {
        // Given — 1,50,000 of the 3,20,000 under s.131, entered there too
        HousingLoan split = new HousingLoan(LoanUse.ACQUISITION_OR_CONSTRUCTION, BORROWED, Money.of("320000"),
                Money.ZERO, Money.of("150000"), true, false);
        PurposeLoanInterest section131 = new PurposeLoanInterest(LoanPurpose.RESIDENTIAL_HOUSE, Money.of("150000"),
                LocalDate.of(2019, 6, 1), Money.of("3000000"), Optional.empty(), Optional.of(Money.of("4000000")),
                false);

        // When
        NodeResult result = run(2026, OLD, List.of(let(ACQUIRED, split)), List.of(section131));

        // Then — s.131(3): 3,65,400 − 1,70,000
        assertThat(result.value()).isEqualTo(rupees("195400"));
    }

    @Test
    void evaluate_whenTheInterestLeftOutDiffersFromTheChapterEightLoans_isRefused() {
        // Given — 1,50,000 said to be under s.131, but the s.131 loan carries 1,00,000
        HousingLoan split = new HousingLoan(LoanUse.ACQUISITION_OR_CONSTRUCTION, BORROWED, Money.of("320000"),
                Money.ZERO, Money.of("150000"), true, false);
        PurposeLoanInterest section131 = new PurposeLoanInterest(LoanPurpose.RESIDENTIAL_HOUSE, Money.of("100000"),
                LocalDate.of(2019, 6, 1), Money.of("3000000"), Optional.empty(), Optional.of(Money.of("4000000")),
                false);

        // When / Then
        assertThatThrownBy(() -> run(2026, OLD, List.of(let(ACQUIRED, split)), List.of(section131)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("enter the same amount on both sides");
    }

    @Test
    void evaluate_whenTheInterestIsPayableAbroadUntaxed_deductsNothing() {
        // Given
        HousingLoan abroad = new HousingLoan(LoanUse.ACQUISITION_OR_CONSTRUCTION, BORROWED, Money.of("320000"),
                Money.ZERO, Money.ZERO, true, true);

        // When
        NodeResult result = run(2026, OLD, List.of(let(ACQUIRED, abroad)));

        // Then — s.22(6): 3,65,400
        assertThat(result.value()).isEqualTo(rupees("365400"));
        assertThat(result.trace()).filteredOn(line -> line.rule().equals(RuleIds.HP_INTEREST)).singleElement()
                .satisfies(line -> assertThat(line.out()).isEqualTo(MoneyValue.of(Money.ZERO)));
    }

    @Test
    void evaluate_whenACoOwnerPaysTheirOwnInterest_deductsItFromTheirShareUnshared() {
        // Given — half of R16's let house; the co-owner's own interest 1,00,000
        HouseProperty half = new HouseProperty(HouseOccupancy.LET, Money.of("480000"), Money.of("540000"), Money.ZERO, false,
                Money.of("18000"), Rate.percent("50"), Money.ZERO, Optional.of(ACQUIRED),
                List.of(loan("100000", "0", true)));

        // When
        NodeResult result = run(2026, OLD, List.of(half));

        // Then — S2 s.24(2): half of 3,65,400 = 1,82,700, less 1,00,000
        assertThat(result.value()).isEqualTo(rupees("82700"));
    }

    @Test
    void evaluate_whenTheHeadIsALoss_isRefusedUntilSetOff() {
        // When / Then — R16's own residence alone: −2,00,000
        assertThatThrownBy(() -> run(2026, OLD, List.of(home(ACQUIRED, R16_LOAN))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("loss of 200000")
                .hasMessageContaining("W-081");
    }

    @Test
    void evaluate_whenTheHouseIsAcquiredAfterTheTaxYear_isRefused() {
        // When / Then
        assertThatThrownBy(() -> run(2026, OLD, List.of(let(LocalDate.of(2027, 4, 1), R16_LOAN))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("after the tax year");
    }

    @Test
    void construction_whenALoanToAcquireHasNoAcquisitionDate_isRefused() {
        // When / Then
        assertThatThrownBy(() -> new HouseProperty(HouseOccupancy.LET, Money.of("480000"), Money.of("540000"), Money.ZERO,
                        false, Money.ZERO, WHOLE, Money.ZERO, Optional.empty(), List.of(R16_LOAN)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("needs the date it was acquired or constructed");
    }

    @Test
    void construction_whenARepairLoanCarriesPreConstructionInterest_isRefused() {
        // When / Then
        assertThatThrownBy(() -> new HousingLoan(LoanUse.REPAIR_OR_RECONSTRUCTION, BORROWED, Money.of("1000"),
                        Money.of("1000"), Money.ZERO, true, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("acquire or construct");
    }
}
