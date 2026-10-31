package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.input.EducationLoanInterest;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.LoanPurpose;
import com.thiru.wealthlens.taxengine.input.PurposeLoanInterest;
import com.thiru.wealthlens.taxengine.pipeline.ComponentSet;
import com.thiru.wealthlens.taxengine.pipeline.HeadsAfterSetOff;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.SalaryBases;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.test.LockedInstruments;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** s.129–132 through {@link ChapterEightNode}, TY 2026-27 (S2; D-139). Figures by hand from the sections. */
class LoanInterestDeductionTest {

    private static final RegimeCode OLD = new RegimeCode("FIRST_SCHEDULE_RATES");
    private static final RegimeCode NEW = new RegimeCode("NEW_TAX_REGIME");
    private static final TaxYearId TY_2026 = TaxYearId.of(2026);

    private static NodeResult run(RegimeCode regime, List<EducationLoanInterest> education,
            List<PurposeLoanInterest> loans) {
        return run(TY_2026, regime, education, loans);
    }

    private static NodeResult run(TaxYearId year, RegimeCode regime, List<EducationLoanInterest> education,
            List<PurposeLoanInterest> loans) {
        RulePack pack = new ClasspathRulePackProvider(
                        "rulepacks", Set.of(year), new JsonPackAssembler(LockedInstruments.of(year)))
                .load(year);
        EngineInputs plain = NodeHarness.plainTaxpayer();
        EngineInputs inputs = new EngineInputs(year, regime,
                TemporalContext.forTaxYear(year, year.startsOn().plusMonths(5)),
                new EnteredFacts(List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of(), List.of(),
                        List.of(), List.of(), education, loans, Optional.empty(), Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty()),
                plain.assessee(), plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(),
                plain.filing());
        return NodeHarness.with(pack).regime(regime).inputs(inputs)
                .given(NodeIds.GTI, Money.of("2000000"))
                .given(NodeIds.SETOFF, new HeadsAfterSetOff(Money.of("2000000"), Map.of()))
                .given(NodeIds.INPUTS_COMPONENTS, new ComponentSet(Map.of()))
                .given(NodeIds.SALARY_BASE, new SalaryBases(Map.of()))
                .run(new ChapterEightNode());
    }

    private static NodeResult education(String interest, int initialYear) {
        return run(OLD, List.of(new EducationLoanInterest(Money.of(interest), TaxYearId.of(initialYear))), List.of());
    }

    private static PurposeLoanInterest house(String interest, LocalDate sanctioned, String sanctionedAmount,
            Optional<String> value, Optional<String> stampDuty, boolean ownedHouse) {
        return new PurposeLoanInterest(LoanPurpose.RESIDENTIAL_HOUSE, Money.of(interest), sanctioned,
                Money.of(sanctionedAmount), value.map(Money::of), stampDuty.map(Money::of), ownedHouse);
    }

    private static NodeResult loan(PurposeLoanInterest loan) {
        return run(OLD, List.of(), List.of(loan));
    }

    @Test
    void evaluate_whenTaxYear2025EducationLoanInterestIsEntered_allowsItWithinSection80esSpan() {
        // When — 1961 s.80E (D-147): started 2020-21, its span runs to 2027-28; started 2016-17, it ended 2023-24
        NodeResult within = run(TaxYearId.of(2025), OLD,
                List.of(new EducationLoanInterest(Money.of("80000"), TaxYearId.of(2020))), List.of());
        NodeResult ended = run(TaxYearId.of(2025), OLD,
                List.of(new EducationLoanInterest(Money.of("80000"), TaxYearId.of(2016))), List.of());

        // Then
        assertThat(within.value()).isEqualTo(MoneyValue.of(Money.of("80000")));
        assertThat(ended.value()).isEqualTo(MoneyValue.of(Money.ZERO));
    }

    @Test
    void evaluate_whenTaxYear2025HouseLoansAreEntered_triesSection80eeThenSection80eea() {
        // When — 80EE: sanctioned 15-6-2016, 30 lakh, value 45 lakh; 80EEA: sanctioned 10-5-2021, stamp duty 40 lakh
        NodeResult earlier = run(TaxYearId.of(2025), OLD, List.of(), List.of(house("70000", LocalDate.of(2016, 6, 15),
                "3000000", Optional.of("4500000"), Optional.empty(), false)));
        NodeResult later = run(TaxYearId.of(2025), OLD, List.of(), List.of(house("200000", LocalDate.of(2021, 5, 10),
                "4000000", Optional.empty(), Optional.of("4000000"), false)));
        NodeResult outside = run(TaxYearId.of(2025), OLD, List.of(), List.of(house("200000", LocalDate.of(2022, 5, 10),
                "4000000", Optional.empty(), Optional.of("4000000"), false)));

        // Then — 50,000 (80EE); 1,50,000 (80EEA, sanctioned to 31-3-2022); nil after the window
        assertThat(earlier.value()).isEqualTo(MoneyValue.of(Money.of("50000")));
        assertThat(later.value()).isEqualTo(MoneyValue.of(Money.of("150000")));
        assertThat(outside.value()).isEqualTo(MoneyValue.of(Money.ZERO));
    }

    @Test
    void evaluate_whenATaxYear2025ElectricVehicleLoanIsEntered_allowsItsInterest() {
        // When — 1961 s.80EEB: sanctioned 1-1-2023, within 1-4-2019 to 31-3-2023
        NodeResult result = run(TaxYearId.of(2025), OLD, List.of(), List.of(new PurposeLoanInterest(
                LoanPurpose.ELECTRIC_VEHICLE, Money.of("90000"), LocalDate.of(2023, 1, 1), Money.of("1200000"),
                Optional.empty(), Optional.empty(), false)));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("90000")));
    }

    @Test
    void evaluate_whenTheEducationLoanIsInItsFifthYear_allowsAllTheInterest() {
        // When — s.129(2): 2022-23 and the seven after run to 2029-30
        NodeResult result = education("80000", 2022);

        // Then — no limit on the amount
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("80000")));
    }

    @Test
    void evaluate_whenTheEducationLoansSpanHasEnded_allowsNothingAndSaysWhy() {
        // When — 2018-19 and the seven after end with 2025-26
        NodeResult result = education("80000", 2018);

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(result.trace()).singleElement()
                .satisfies(line -> assertThat(line.limitedBy()).hasValueSatisfying(
                        reason -> assertThat(reason).contains("outside the initial tax year")));
    }

    @Test
    void evaluate_whenTheEducationLoanIsInItsEighthYear_stillAllowsTheInterest() {
        // When — 2019-20 and the seven after end with 2026-27, the last year allowed
        NodeResult result = education("80000", 2019);

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("80000")));
    }

    @Test
    void evaluate_whenAHouseLoanFrom2016Qualifies_allowsFiftyThousandUnderSection130() {
        // When — sanctioned 1-9-2016, 30 lakh, value 45 lakh, no house owned; interest 70,000
        NodeResult result = loan(house("70000", LocalDate.of(2016, 9, 1), "3000000", Optional.of("4500000"),
                Optional.empty(), false));

        // Then — s.130(2)
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("50000")));
        assertThat(result.trace()).extracting(TraceLine::rule).containsExactly(RuleIds.DED_HOUSE_LOAN_EARLIER);
    }

    @Test
    void evaluate_whenA2016HouseIsWorthTooMuch_triesSection131AndFailsItsWindow() {
        // When — value 55 lakh fails s.130(3)(c); sanctioned 2016, outside s.131's 2019–2022
        NodeResult result = loan(house("70000", LocalDate.of(2016, 9, 1), "3000000", Optional.of("5500000"),
                Optional.empty(), false));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(result.trace()).singleElement().satisfies(line -> {
            assertThat(line.rule()).isEqualTo(RuleIds.DED_HOUSE_LOAN);
            assertThat(line.limitedBy()).hasValueSatisfying(reason -> assertThat(reason).contains("outside"));
        });
    }

    @Test
    void evaluate_whenAHouseLoanFrom2020Qualifies_allowsOneLakhFiftyThousandUnderSection131() {
        // When — sanctioned 1-6-2020, stamp duty value 40 lakh, no house owned; interest 1,80,000
        NodeResult result = loan(house("180000", LocalDate.of(2020, 6, 1), "3500000", Optional.empty(),
                Optional.of("4000000"), false));

        // Then — s.131(1)
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("150000")));
        assertThat(result.trace()).extracting(TraceLine::rule).containsExactly(RuleIds.DED_HOUSE_LOAN);
    }

    @Test
    void evaluate_whenAHouseWasOwnedOnSanction_allowsNothing() {
        // When — s.131(2)(c)
        NodeResult result = loan(house("100000", LocalDate.of(2020, 6, 1), "3500000", Optional.empty(),
                Optional.of("4000000"), true));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
    }

    @Test
    void evaluate_whenAnElectricVehicleLoanWasSanctionedAfterMarch2023_allowsNothing() {
        // When — s.132(2): sanctioned by 31-3-2023
        NodeResult result = loan(new PurposeLoanInterest(LoanPurpose.ELECTRIC_VEHICLE, Money.of("60000"),
                LocalDate.of(2023, 5, 1), Money.of("1000000"), Optional.empty(), Optional.empty(), false));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
    }

    @Test
    void evaluate_whenAnElectricVehicleLoanQualifies_allowsTheInterest() {
        // When — sanctioned 1-5-2022; interest 60,000 within 1,50,000
        NodeResult result = loan(new PurposeLoanInterest(LoanPurpose.ELECTRIC_VEHICLE, Money.of("60000"),
                LocalDate.of(2022, 5, 1), Money.of("1000000"), Optional.empty(), Optional.empty(), false));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("60000")));
    }

    @Test
    void evaluate_whenClaimedInTheNewRegime_givesANilLine() {
        // When — s.202(2)(a)(xii) keeps none of these
        NodeResult result = run(NEW, List.of(new EducationLoanInterest(Money.of("80000"), TaxYearId.of(2022))),
                List.of());

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(result.trace()).singleElement()
                .satisfies(line -> assertThat(line.limitedBy()).hasValueSatisfying(reason -> assertThat(reason).startsWith("disallowed in the NEW_TAX_REGIME regime: ")
                        .endsWith("(" + RuleIds.REG_NEW_TAX_REGIME_NO_EXEMPTION + ")")));
    }
}
