package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.input.Assessee.ResidentialStatus;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.FilingFacts;
import com.thiru.wealthlens.taxengine.input.Individual;
import com.thiru.wealthlens.taxengine.pipeline.AdvanceTaxPosition;
import com.thiru.wealthlens.taxengine.pipeline.InterestAndFees;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.test.LockedInstruments;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import com.thiru.wealthlens.taxengine.trace.Notice;
import com.thiru.wealthlens.taxengine.trace.Severity;
import com.thiru.wealthlens.taxengine.trace.TraceAnnotation;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Advance tax, the interest on it and on a late return, and the late-return fee (W-089c, W-112, D-446; 2025 ss.403 – 408,
 * 423 – 425, 428; 1961 ss.207 – 211, 234A – 234C, 234F; Rules 2026 r.269). Every figure is worked by hand in
 * {@code plans/W-089c.md}: a bill of 1,00,000, tax at source 20,000, advance tax 30,000 on 15 September and 20,000 on
 * 15 March, the return due on 31 July after the year.
 */
class AdvanceTaxAndInterestTest {

    private static final RegimeCode REGIME = new RegimeCode("FIRST_SCHEDULE_RATES");

    private static final LocalDate UNDER_SIXTY = LocalDate.of(1985, 6, 15);

    private static RulePack pack(int year) {
        TaxYearId taxYear = TaxYearId.of(year);
        return new ClasspathRulePackProvider("rulepacks", Set.of(taxYear),
                new JsonPackAssembler(LockedInstruments.of(taxYear))).load(taxYear);
    }

    /** The worked case's facts for the year beginning 1 April {@code year}, furnished as given. */
    private static FilingFacts filing(int year, Optional<LocalDate> furnishedOn, List<FilingFacts.Payment> selfAssessment) {
        return new FilingFacts(LocalDate.of(year + 1, 7, 31), furnishedOn,
                List.of(paid(LocalDate.of(year, 9, 15), "30000"), paid(LocalDate.of(year + 1, 3, 15), "20000")),
                Money.of("20000"), selfAssessment);
    }

    private static FilingFacts.Payment paid(LocalDate on, String amount) {
        return new FilingFacts.Payment(on, Money.of(amount));
    }

    private static EngineInputs inputs(int year, FilingFacts filing, LocalDate asOf, LocalDate born) {
        TaxYearId taxYear = TaxYearId.of(year);
        EngineInputs plain = NodeHarness.plainTaxpayer();
        return new EngineInputs(taxYear, REGIME, TemporalContext.forTaxYear(taxYear, asOf), plain.entered(),
                new Individual(born, ResidentialStatus.RESIDENT, Optional.empty(), Optional.empty()), plain.family(),
                plain.ledgers(), plain.elections(), plain.capitalGains(), filing);
    }

    private static NodeResult advance(int year, String bill, EngineInputs inputs) {
        return NodeHarness.with(pack(year)).regime(REGIME).inputs(inputs).given(NodeIds.TAX_NETPAYABLE, Money.of(bill))
                .run(new AdvanceTaxNode());
    }

    private static NodeResult owed(int year, String bill, String totalIncome, EngineInputs inputs) {
        AdvanceTaxPosition position = (AdvanceTaxPosition) advance(year, bill, inputs).value();
        return NodeHarness.with(pack(year)).regime(REGIME).inputs(inputs).given(NodeIds.TAX_NETPAYABLE, Money.of(bill))
                .given(NodeIds.ADVANCE_TAX, position).given(NodeIds.ROUND_TOTALINCOME, Money.of(totalIncome))
                .run(new InterestAndFeeNode());
    }

    private static Map<RuleId, Money> byRule(NodeResult result) {
        return ((InterestAndFees) result.value()).items().stream()
                .collect(Collectors.toMap(InterestAndFees.Item::rule, InterestAndFees.Item::amount));
    }

    private static List<Severity> severities(NodeResult result) {
        return result.annotations().stream().map(TraceAnnotation::notices).flatMap(List::stream).map(Notice::severity).toList();
    }

    private static Optional<LocalDate> on(int y, int m, int d) {
        return Optional.of(LocalDate.of(y, m, d));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenTheWorkedCaseIsLiable_owesEachInstalmentLessWhatWasPaidByThen(int year) {
        // Given — 1,00,000 − 20,000 = 80,000; 15 / 45 / 75 / 100 % = 12,000 / 36,000 / 60,000 / 80,000
        EngineInputs inputs = inputs(year, filing(year, Optional.empty(), List.of()), LocalDate.of(year, 4, 1), UNDER_SIXTY);

        // When
        AdvanceTaxPosition position = (AdvanceTaxPosition) advance(year, "100000", inputs).value();

        // Then — paid by then 0 / 30,000 / 30,000 / 50,000
        assertThat(position.liable()).isTrue();
        assertThat(position.amount()).isEqualTo(Money.of("80000"));
        assertThat(position.instalments()).containsExactly(
                new AdvanceTaxPosition.Instalment(LocalDate.of(year, 6, 15), Money.of("12000"), Money.ZERO, Money.of("12000")),
                new AdvanceTaxPosition.Instalment(LocalDate.of(year, 9, 15), Money.of("36000"), Money.of("30000"), Money.of("6000")),
                new AdvanceTaxPosition.Instalment(LocalDate.of(year, 12, 15), Money.of("60000"), Money.of("30000"), Money.of("30000")),
                new AdvanceTaxPosition.Instalment(LocalDate.of(year + 1, 3, 15), Money.of("80000"), Money.of("50000"),
                        Money.of("30000")));
    }

    @ParameterizedTest(name = "bill {0} → liable {1}")
    @CsvSource({"29999, false", "30000, true"})
    void evaluate_whenAdvanceTaxIsEitherSideOfTenThousand_isLiableFromTenThousand(String bill, boolean liable) {
        // Given — less 20,000 at source: 9,999 is below s.404's ₹10,000; 10,000 is "₹10000 or more"
        EngineInputs inputs = inputs(2026, filing(2026, Optional.empty(), List.of()), LocalDate.of(2026, 4, 1), UNDER_SIXTY);

        // When
        AdvanceTaxPosition position = (AdvanceTaxPosition) advance(2026, bill, inputs).value();

        // Then
        assertThat(position.liable()).isEqualTo(liable);
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenTheWorkedCaseIsFiledAMonthLate_chargesEachInterestAndTheFee(int year) {
        // Given — filed 31 August after a due date of 31 July
        EngineInputs inputs = inputs(year, filing(year, on(year + 1, 8, 31), List.of()), LocalDate.of(year + 1, 8, 31),
                UNDER_SIXTY);

        // When
        NodeResult result = owed(year, "100000", "1200000", inputs);

        // Then — 234A 30,000 × 1 % × 1 = 300; 234B 30,000 × 1 % × 5 = 1,500; 234C 360 + 0 + 900 + 300 = 1,560; fee 5,000
        assertThat(byRule(result)).containsExactlyInAnyOrderEntriesOf(Map.of(
                RuleIds.INT_LATE_RETURN, Money.of("300"), RuleIds.INT_ADVANCE_SHORTFALL, Money.of("1500"),
                RuleIds.INT_DEFERMENT, Money.of("1560"), RuleIds.FEE_LATE_RETURN, Money.of("5000")));
        assertThat(result.value().asMoney()).isEqualTo(Money.of("8360"));
    }

    @Test
    void evaluate_whenTheReturnIsNotYetFiled_runsTheInterestToTheAsOfDate() {
        // Given — unfiled on 6-11-2026: 234A 1-8 to 6-11 = 4 months; 234B 1-4 to 6-11 = 8 months (D-446 C2a)
        EngineInputs inputs = inputs(2025, filing(2025, Optional.empty(), List.of()), LocalDate.of(2026, 11, 6), UNDER_SIXTY);

        // When
        NodeResult result = owed(2025, "100000", "1200000", inputs);

        // Then — 1,200 + 2,400 + 1,560 + 5,000 = 10,160
        assertThat(byRule(result)).containsExactlyInAnyOrderEntriesOf(Map.of(
                RuleIds.INT_LATE_RETURN, Money.of("1200"), RuleIds.INT_ADVANCE_SHORTFALL, Money.of("2400"),
                RuleIds.INT_DEFERMENT, Money.of("1560"), RuleIds.FEE_LATE_RETURN, Money.of("5000")));
    }

    @Test
    void evaluate_whenPlanningInSeptember_chargesOnlyTheJuneInstalmentAlreadyMissed() {
        // Given — 1-9-2025: only 15 June has passed; nothing is due on the return yet
        EngineInputs inputs = inputs(2025, filing(2025, Optional.empty(), List.of()), LocalDate.of(2025, 9, 1), UNDER_SIXTY);

        // When
        NodeResult result = owed(2025, "100000", "1200000", inputs);

        // Then — 12,000 short at June × 3 % = 360
        assertThat(byRule(result)).containsExactlyEntriesOf(Map.of(RuleIds.INT_DEFERMENT, Money.of("360")));
    }

    @Test
    void evaluate_whenAResidentTurnsSixtyInTheYear_chargesNoAdvanceTaxInterest() {
        // Given — born 1-1-1965, 61 on 31-3-2026: not liable (1961 s.207(2)); late filing still owes 234A and the fee
        EngineInputs inputs = inputs(2025, filing(2025, on(2026, 8, 31), List.of()), LocalDate.of(2026, 8, 31),
                LocalDate.of(1965, 1, 1));

        // When
        NodeResult result = owed(2025, "100000", "1200000", inputs);

        // Then — 300 + 5,000; with the 30,000 balance the payable is 35,300
        assertThat(byRule(result)).containsExactlyInAnyOrderEntriesOf(Map.of(
                RuleIds.INT_LATE_RETURN, Money.of("300"), RuleIds.FEE_LATE_RETURN, Money.of("5000")));
    }

    @Test
    void evaluate_whenFiledOnTheDueDate_chargesNoLateInterestAndNoFee() {
        // Given — furnished 31-7-2026, the due date
        EngineInputs inputs = inputs(2025, filing(2025, on(2026, 7, 31), List.of()), LocalDate.of(2026, 7, 31), UNDER_SIXTY);

        // When
        NodeResult result = owed(2025, "100000", "1200000", inputs);

        // Then — 234B April to July = 4 months × 300 = 1,200; 234C 1,560
        assertThat(byRule(result)).containsExactlyInAnyOrderEntriesOf(Map.of(
                RuleIds.INT_ADVANCE_SHORTFALL, Money.of("1200"), RuleIds.INT_DEFERMENT, Money.of("1560")));
    }

    @ParameterizedTest(name = "paid {0} → shortfall interest {1}")
    @CsvSource({"71999, 400", "72000, 0"})
    void evaluate_whenAdvanceTaxIsEitherSideOfNinetyPercent_chargesShortfallInterestBelowIt(String paid, String interest) {
        // Given — assessed 80,000; 90 % = 72,000, and s.234B runs only on "less than ninety per cent."
        FilingFacts filing = new FilingFacts(LocalDate.of(2026, 7, 31), on(2026, 8, 31),
                List.of(paid(LocalDate.of(2025, 9, 15), paid)), Money.of("20000"), List.of());
        EngineInputs inputs = inputs(2025, filing, LocalDate.of(2026, 8, 31), UNDER_SIXTY);

        // When
        NodeResult result = owed(2025, "100000", "1200000", inputs);

        // Then — 71,999: shortfall 8,001 → 8,000 × 1 % × 5 = 400; 72,000: nil
        assertThat(byRule(result).getOrDefault(RuleIds.INT_ADVANCE_SHORTFALL, Money.ZERO)).isEqualTo(Money.of(interest));
    }

    @Test
    void evaluate_whenTheAmountIsNotAMultipleOfAHundred_roundsItDownFirst() {
        // Given — bill 1,00,050: 234B shortfall 30,050 → 30,000 (r.269(c))
        EngineInputs inputs = inputs(2026, filing(2026, on(2027, 8, 31), List.of()), LocalDate.of(2027, 8, 31), UNDER_SIXTY);

        // When
        NodeResult result = owed(2026, "100050", "1200000", inputs);

        // Then — 30,000 × 1 % × 5 = 1,500, not 1,502.50
        assertThat(byRule(result)).containsEntry(RuleIds.INT_ADVANCE_SHORTFALL, Money.of("1500"));
    }

    @Test
    void evaluate_whenTaxIsPaidOnTheReturnEarly_stopsTheShortfallInterestOnIt() {
        // Given — the whole 30,000 paid on 10-6-2026 (s.234B(2)): 1-4 to 10-6 = 3 months, nothing after
        EngineInputs inputs = inputs(2025, filing(2025, on(2026, 8, 31), List.of(paid(LocalDate.of(2026, 6, 10), "30000"))),
                LocalDate.of(2026, 8, 31), UNDER_SIXTY);

        // When
        NodeResult result = owed(2025, "100000", "1200000", inputs);

        // Then — 30,000 × 1 % × 3 = 900
        assertThat(byRule(result)).containsEntry(RuleIds.INT_ADVANCE_SHORTFALL, Money.of("900"));
    }

    @ParameterizedTest(name = "TY {0}: late return {1}")
    @CsvSource({"2025, 600, false", "2026, 600, true"})
    void evaluate_whenFiledFiveDaysIntoAMonth_countsThePartMonthWhole(int year, String lateInterest, boolean oq39) {
        // Given — furnished 5 September: 1 August to 5 September is one month and five days → 2 months (D-446 C3a)
        EngineInputs inputs = inputs(year, filing(year, on(year + 1, 9, 5), List.of()), LocalDate.of(year + 1, 9, 5), UNDER_SIXTY);

        // When
        NodeResult result = owed(year, "100000", "1200000", inputs);

        // Then — 30,000 × 1 % × 2; 2025 s.423 does not say "part of a month", so TY 2026-27 says it read it so (OQ-39)
        assertThat(byRule(result)).containsEntry(RuleIds.INT_LATE_RETURN, Money.of(lateInterest));
        assertThat(result.annotations().stream().flatMap(a -> a.notices().stream())
                .anyMatch(n -> n.message().contains("OQ-39"))).isEqualTo(oq39);
    }

    @ParameterizedTest(name = "TY {0}: unverified notice {1}")
    @CsvSource({"2025, true", "2026, false"})
    void evaluate_whenInterestIsCharged_flagsTheUnreadRuleOnlyForTheYearThatRestsOnIt(int year, boolean flagged) {
        // Given — TY 2025-26's calculation row rests on Rules 1962 r.119A, not read (D-446 C4a)
        EngineInputs inputs = inputs(year, filing(year, on(year + 1, 8, 31), List.of()), LocalDate.of(year + 1, 8, 31), UNDER_SIXTY);

        // When
        NodeResult result = owed(year, "100000", "1200000", inputs);

        // Then
        assertThat(severities(result).contains(Severity.UNVERIFIED_RULE)).isEqualTo(flagged);
    }

    @ParameterizedTest(name = "TY {0}, income {1}: fee {2}")
    @CsvSource({"2025, 500000, 1000", "2025, 500001, 5000", "2026, 500000, 1000", "2026, 500001, 5000"})
    void evaluate_whenTotalIncomeIsAtTheLowerFeesLimit_chargesTheLowerFee(int year, String income, String fee) {
        // Given — "does not exceed ₹5,00,000", so 5,00,000 itself is the lower fee
        EngineInputs inputs = inputs(year, filing(year, on(year + 1, 8, 31), List.of()), LocalDate.of(year + 1, 8, 31), UNDER_SIXTY);

        // When
        NodeResult result = owed(year, "100000", income, inputs);

        // Then
        assertThat(byRule(result)).containsEntry(RuleIds.FEE_LATE_RETURN, Money.of(fee));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenTheBalanceHasInterestOnIt_roundsTheirSum(int year) {
        // Given — balance 30,000 with 8,360 of interest and fee (D-446 C5a)
        InterestAndFees owed = new InterestAndFees(List.of(
                new InterestAndFees.Item(RuleIds.INT_DEFERMENT, InterestAndFees.Kind.INTEREST, Money.of("3363")),
                new InterestAndFees.Item(RuleIds.FEE_LATE_RETURN, InterestAndFees.Kind.FEE, Money.of("5000"))));

        // When
        NodeResult result = NodeHarness.with(pack(year)).regime(REGIME)
                .inputs(inputs(year, filing(year, Optional.empty(), List.of()), LocalDate.of(year, 4, 1), UNDER_SIXTY))
                .given(NodeIds.TAX_BALANCE, Money.of("30000"))
                .given(NodeIds.INTEREST_AND_FEES, owed).run(new RoundNetPayableNode());

        // Then — 38,363 to the nearest ₹10 = 38,360
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("38360")));
    }
}
