package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.thiru.wealthlens.taxengine.input.CarriedCapitalLoss;
import com.thiru.wealthlens.taxengine.input.CarriedHousePropertyLoss;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.LedgerSnapshot;
import com.thiru.wealthlens.taxengine.pipeline.CapitalGainsHead;
import com.thiru.wealthlens.taxengine.pipeline.HeadsAfterSetOff;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsPart;
import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsParts;
import com.thiru.wealthlens.taxengine.rules.definition.LossCarryForward;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinition;
import com.thiru.wealthlens.taxengine.rules.definition.TaxYearSpan;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.test.LockedInstruments;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Set-off of a head's loss against the other heads in the year (S2 s.109; 1961 s.71; D-217). Fixture amounts, each
 * figure worked by hand; the ₹2,00,000 is S2 s.109(1)(b) / 1961 s.71(3A).
 */
class SetOffNodeTest {

    private static final CapitalGainsPart SHORT_TERM_SLAB = new CapitalGainsPart("SHORT_TERM_SLAB");

    private static final CapitalGainsPart SHORT_TERM_LISTED_EQUITY = new CapitalGainsPart("SHORT_TERM_LISTED_EQUITY");

    private static final CapitalGainsPart LONG_TERM_OTHER = new CapitalGainsPart("LONG_TERM_OTHER");

    private static final CapitalGainsPart LONG_TERM_LISTED_EQUITY = new CapitalGainsPart("LONG_TERM_LISTED_EQUITY");

    private static final RegimeCode OLD = new RegimeCode("FIRST_SCHEDULE_RATES");
    private static final RegimeCode NEW = new RegimeCode("NEW_TAX_REGIME");

    private static RulePack pack(int year) {
        TaxYearId taxYear = TaxYearId.of(year);
        return new ClasspathRulePackProvider("rulepacks", Set.of(taxYear),
                new JsonPackAssembler(LockedInstruments.of(taxYear))).load(taxYear);
    }

    private static NodeResult run(int year, RegimeCode regime, String salaries, String houseProperty,
            String otherSources) {
        return run(year, regime, salaries, houseProperty, otherSources, Map.of());
    }

    private static NodeResult run(int year, RegimeCode regime, String salaries, String houseProperty,
            String otherSources, Map<TaxYearId, CarriedHousePropertyLoss> carried) {
        return run(year, regime, salaries, houseProperty, otherSources, carried,
                NodeHarness.plainTaxpayer().entered());
    }

    private static NodeResult run(int year, RegimeCode regime, String salaries, String houseProperty,
            String otherSources, Map<TaxYearId, CarriedHousePropertyLoss> carried, EnteredFacts entered) {
        TaxYearId taxYear = TaxYearId.of(year);
        EngineInputs plain = NodeHarness.plainTaxpayer();
        LedgerSnapshot empty = LedgerSnapshot.empty();
        LedgerSnapshot ledgers = new LedgerSnapshot(empty.leaveTravelByBlock(), empty.vrsClaimedIn(),
                empty.gratuityAlreadyAllowed(), empty.leaveEncashmentAlreadyAllowed(), carried,
                empty.educationLoanInterestFirstYear(), Map.of());
        EngineInputs inputs = new EngineInputs(taxYear, regime,
                TemporalContext.forTaxYear(taxYear, LocalDate.of(year, 9, 21)), entered, plain.assessee(),
                plain.family(), ledgers, plain.elections(), plain.capitalGains(), plain.filing());
        return NodeHarness.with(pack(year)).regime(regime).inputs(inputs)
                .given(NodeIds.SALARY_INCOME, Money.of(salaries))
                .given(NodeIds.HOUSEPROPERTY_INCOME, Money.of(houseProperty))
                .given(NodeIds.OTHERSOURCES_INCOME, Money.of(otherSources))
                .given(NodeIds.CAPITALGAINS_INCOME, CapitalGainsHead.none())
                .run(new SetOffNode());
    }

    private static MoneyValue rupees(String amount) {
        return MoneyValue.of(Money.of(amount));
    }

    private static List<TraceLine> lines(NodeResult result, StepKind kind) {
        return result.trace().stream().filter(line -> line.kind() == kind).toList();
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenAHousePropertyLossIsUnderTheLimit_setsItOffWhole(int year) {
        // When — 10,00,000 salary, a 1,50,000 house-property loss
        NodeResult result = run(year, OLD, "1000000", "-150000", "0");

        // Then — 10,00,000 − 1,50,000
        assertThat(result.value().asMoney()).isEqualTo(Money.of("850000"));
        assertThat(lines(result, StepKind.SET_OFF)).singleElement().satisfies(line -> {
            assertThat(line.rule()).isEqualTo(RuleIds.SETOFF_HOUSE_PROPERTY);
            assertThat(line.in()).isEqualTo(rupees("1000000"));
            assertThat(line.out()).isEqualTo(rupees("150000"));
            assertThat(line.limitedBy().orElseThrow()).contains("HOUSE_PROPERTY loss against SALARIES")
                    .contains("[SALARIES, HOUSE_PROPERTY, OTHER_SOURCES, CAPITAL_GAINS]").contains("the declared default").contains("D-221");
        });
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenAHousePropertyLossExceedsTheLimit_setsOffTwoLakhAndCarriesTheRest(int year) {
        // When — a 3,00,000 loss against 10,00,000 salary
        NodeResult result = run(year, OLD, "1000000", "-300000", "0");

        // Then — 2,00,000 set off, 10,00,000 − 2,00,000; 1,00,000 left to carry forward (W-081b)
        assertThat(result.value().asMoney()).isEqualTo(Money.of("800000"));
        assertThat(lines(result, StepKind.SET_OFF)).extracting(TraceLine::out).containsExactly(rupees("200000"));
        assertThat(lines(result, StepKind.MEASURE)).extracting(TraceLine::in, TraceLine::out)
                .containsExactly(tuple(rupees("300000"), rupees("200000")), tuple(rupees("300000"), rupees("100000")));
        assertThat(lines(result, StepKind.MEASURE).get(1).limitedBy().orElseThrow()).contains("carried forward")
                .contains("W-081b");
    }

    @Test
    void evaluate_whenTheOtherHeadsHoldLessThanTheLoss_setsOffWhatTheyHold() {
        // When — a 1,50,000 loss against 50,000 salary
        NodeResult result = run(2026, OLD, "50000", "-150000", "0");

        // Then — 50,000 set off, nil left under the heads, 1,00,000 to carry forward
        assertThat(result.value().asMoney()).isEqualTo(Money.of("0"));
        assertThat(lines(result, StepKind.SET_OFF)).extracting(TraceLine::out).containsExactly(rupees("50000"));
        assertThat(lines(result, StepKind.MEASURE).get(1).out()).isEqualTo(rupees("100000"));
    }

    @Test
    void evaluate_whenTwoHeadsCanAbsorbTheLoss_takesSalariesFirstThenOtherSources() {
        // When — a 1,50,000 loss against 1,00,000 salary and 2,00,000 under other sources
        NodeResult result = run(2026, OLD, "100000", "-150000", "200000");

        // Then — the declared order: 1,00,000 from salaries, then 50,000 from other sources; 1,50,000 left
        assertThat(result.value().asMoney()).isEqualTo(Money.of("150000"));
        assertThat(lines(result, StepKind.SET_OFF)).extracting(TraceLine::in, TraceLine::out)
                .containsExactly(tuple(rupees("100000"), rupees("100000")), tuple(rupees("200000"), rupees("50000")));
        assertThat(lines(result, StepKind.SET_OFF).get(1).limitedBy().orElseThrow())
                .contains("HOUSE_PROPERTY loss against OTHER_SOURCES");
    }

    @Test
    void evaluate_whenTheAssesseeElectsOtherSourcesFirst_setsOffInThatOrder() {
        // Given — the same 1,50,000 loss, 1,00,000 salary and 2,00,000 under other sources, other sources elected first
        EnteredFacts elected = EnteredFacts.none().withAbsorptionOrder(
                List.of(HeadCode.OTHER_SOURCES, HeadCode.HOUSE_PROPERTY, HeadCode.SALARIES, HeadCode.CAPITAL_GAINS));

        // When
        NodeResult result = run(2026, OLD, "100000", "-150000", "200000", Map.of(), elected);

        // Then — all 1,50,000 from other sources, nothing from salaries; 1,00,000 + 50,000 left either way
        assertThat(result.value().asMoney()).isEqualTo(Money.of("150000"));
        assertThat(lines(result, StepKind.SET_OFF)).singleElement().satisfies(line -> {
            assertThat(line.in()).isEqualTo(rupees("200000"));
            assertThat(line.out()).isEqualTo(rupees("150000"));
            assertThat(line.limitedBy().orElseThrow()).contains("HOUSE_PROPERTY loss against OTHER_SOURCES")
                    .contains("[OTHER_SOURCES, HOUSE_PROPERTY, SALARIES, CAPITAL_GAINS]").contains("elected").contains("D-221");
        });
    }

    @Test
    void evaluate_whenTheElectedOrderLeavesOutAHead_isRefused() {
        // Given — house property left out, so its loss would never be taken
        EnteredFacts elected = EnteredFacts.none().withAbsorptionOrder(List.of(HeadCode.OTHER_SOURCES, HeadCode.SALARIES));

        // When / Then
        assertThatThrownBy(() -> run(2026, OLD, "100000", "-150000", "200000", Map.of(), elected))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must name the heads");
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenTheNewRegimeHasAHousePropertyLoss_setsOffNothingAndTheLossIsGone(int year) {
        // When — a 40,000 house-property loss against 10,00,000 salary
        NodeResult result = run(year, NEW, "1000000", "-40000", "0");

        // Then — nothing set off; the loss deemed given full effect to (S2 s.202(2)(b)(ii), (3); 1961 s.115BAC)
        assertThat(result.value().asMoney()).isEqualTo(Money.of("1000000"));
        assertThat(lines(result, StepKind.SET_OFF)).singleElement().satisfies(line -> {
            assertThat(line.out()).isEqualTo(rupees("0"));
            assertThat(line.limitedBy().orElseThrow()).contains("REG-NEW-TAX-REGIME-NO-SETOFF");
        });
        assertThat(lines(result, StepKind.MEASURE)).singleElement().satisfies(line -> {
            assertThat(line.out()).isEqualTo(rupees("40000"));
            assertThat(line.limitedBy().orElseThrow()).contains("deemed to have been given full effect to");
        });
    }

    @Test
    void evaluate_whenOtherSourcesHasALoss_setsItOffWithoutALimit() {
        // When — a 3,00,000 loss under other sources against 10,00,000 salary
        NodeResult result = run(2026, OLD, "1000000", "0", "-300000");

        // Then — the whole 3,00,000 (s.109(1) sets no limit for this head)
        assertThat(result.value().asMoney()).isEqualTo(Money.of("700000"));
        assertThat(lines(result, StepKind.SET_OFF)).singleElement().satisfies(line -> {
            assertThat(line.rule()).isEqualTo(RuleIds.SETOFF_ACROSS_HEADS);
            assertThat(line.out()).isEqualTo(rupees("300000"));
        });
        assertThat(lines(result, StepKind.MEASURE)).isEmpty();
    }

    @Test
    void evaluate_whenAnOtherSourcesLossExceedsTheOtherHeads_isNotCarried() {
        // When — a 1,50,000 loss under other sources against 1,00,000 salary
        NodeResult result = run(2026, OLD, "100000", "0", "-150000");

        // Then — 1,00,000 set off; 50,000 left, which no provision carries forward
        assertThat(result.value().asMoney()).isEqualTo(Money.of("0"));
        assertThat(lines(result, StepKind.MEASURE)).singleElement().satisfies(line -> {
            assertThat(line.out()).isEqualTo(rupees("50000"));
            assertThat(line.limitedBy().orElseThrow()).contains("not carried forward");
        });
    }

    private static CarriedHousePropertyLoss carried(String loss, String attributable, String absorbed) {
        return new CarriedHousePropertyLoss(Money.of(loss), Money.of(attributable), Money.of(absorbed));
    }

    private static List<TraceLine> lines(NodeResult result, com.thiru.wealthlens.taxengine.rules.id.RuleId rule,
            StepKind kind) {
        return result.trace().stream().filter(line -> line.rule().equals(rule) && line.kind() == kind).toList();
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenALossIsBroughtForward_setsItOffAgainstHousePropertyIncome(int year) {
        // When — 5,00,000 salary, 1,00,000 house-property income, 60,000 carried from two years before
        NodeResult result = run(year, OLD, "500000", "100000", "0",
                Map.of(TaxYearId.of(year - 2), carried("60000", "0", "0")));

        // Then — 5,00,000 + (1,00,000 − 60,000) (S2 s.110(1)(a); 1961 s.71B)
        assertThat(result.value().asMoney()).isEqualTo(Money.of("540000"));
        assertThat(lines(result, RuleIds.SETOFF_CARRIED_HOUSE_PROPERTY, StepKind.SET_OFF)).singleElement()
                .satisfies(line -> {
                    assertThat(line.in()).isEqualTo(rupees("100000"));
                    assertThat(line.out()).isEqualTo(rupees("60000"));
                    assertThat(line.limitedBy().orElseThrow()).contains("Schedule BFLA").contains("D-221");
                });
        assertThat(lines(result, RuleIds.SETOFF_CARRIED_HOUSE_PROPERTY, StepKind.MEASURE)).isEmpty();
    }

    @Test
    void evaluate_whenTheCarriedLossExceedsHousePropertyIncome_leavesSalariesAloneAndCarriesTheRest() {
        // When — 50,000 house-property income, 80,000 carried
        NodeResult result = run(2026, OLD, "500000", "50000", "0", Map.of(TaxYearId.of(2024), carried("80000", "0", "0")));

        // Then — only against house property: 50,000 set off, salary untouched, 30,000 still carried
        assertThat(result.value().asMoney()).isEqualTo(Money.of("500000"));
        assertThat(lines(result, RuleIds.SETOFF_CARRIED_HOUSE_PROPERTY, StepKind.MEASURE)).singleElement()
                .satisfies(line -> assertThat(line.out()).isEqualTo(rupees("30000")));
    }

    @Test
    void evaluate_whenLossesAreCarriedFromTwoYears_usesTheOldestFirst() {
        // When — 50,000 house-property income; 30,000 from 2023-24 and 40,000 from 2024-25
        NodeResult result = run(2026, OLD, "0", "50000", "0", Map.of(TaxYearId.of(2023), carried("30000", "0", "0"),
                TaxYearId.of(2024), carried("40000", "0", "0")));

        // Then — 30,000 of 2023-24's, then 20,000 of 2024-25's; 20,000 of it still carried
        assertThat(lines(result, RuleIds.SETOFF_CARRIED_HOUSE_PROPERTY, StepKind.SET_OFF))
                .extracting(TraceLine::out).containsExactly(rupees("30000"), rupees("20000"));
        assertThat(lines(result, RuleIds.SETOFF_CARRIED_HOUSE_PROPERTY, StepKind.SET_OFF).get(0).limitedBy()
                .orElseThrow()).contains("loss of 2023-24");
        assertThat(lines(result, RuleIds.SETOFF_CARRIED_HOUSE_PROPERTY, StepKind.MEASURE)).singleElement()
                .satisfies(line -> assertThat(line.out()).isEqualTo(rupees("20000")));
    }

    @Test
    void evaluate_whenALossIsInItsNinthYear_hasExpired() {
        // When — TY 2026-27: 2017-18's loss is nine years back, 2018-19's eight (S2 s.110(2))
        NodeResult result = run(2026, OLD, "0", "100000", "0", Map.of(TaxYearId.of(2017), carried("50000", "0", "0"),
                TaxYearId.of(2018), carried("10000", "0", "0")));

        // Then — 2018-19's 10,000 set off; 2017-18's 50,000 expired
        assertThat(result.value().asMoney()).isEqualTo(Money.of("90000"));
        assertThat(lines(result, RuleIds.SETOFF_CARRIED_HOUSE_PROPERTY, StepKind.SET_OFF)).extracting(TraceLine::out)
                .containsExactly(rupees("10000"));
        assertThat(lines(result, RuleIds.SETOFF_CARRIED_HOUSE_PROPERTY, StepKind.MEASURE)).singleElement()
                .satisfies(line -> {
                    assertThat(line.out()).isEqualTo(rupees("50000"));
                    assertThat(line.limitedBy().orElseThrow()).contains("2017-18 expired");
                });
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenTheNewRegimeMeetsAnAttributablePart_destroysItAndUsesTheRest(int year) {
        // When — 1,00,000 house-property income; 70,000 carried, 50,000 of it from own-residence interest
        NodeResult result = run(year, NEW, "0", "100000", "0",
                Map.of(TaxYearId.of(year - 1), carried("70000", "50000", "0")));

        // Then — the 50,000 gone (S2 s.202(2)(b)(i), (3)); the 20,000 set off: 1,00,000 − 20,000
        assertThat(result.value().asMoney()).isEqualTo(Money.of("80000"));
        assertThat(lines(result, RuleIds.SETOFF_CARRIED_DISALLOWED_PART, StepKind.SET_OFF)).singleElement()
                .satisfies(line -> assertThat(line.out()).isEqualTo(rupees("0")));
        assertThat(lines(result, RuleIds.SETOFF_CARRIED_DISALLOWED_PART, StepKind.MEASURE)).singleElement()
                .satisfies(line -> {
                    assertThat(line.out()).isEqualTo(rupees("50000"));
                    assertThat(line.limitedBy().orElseThrow()).contains("deemed to have been given full effect to");
                });
        assertThat(lines(result, RuleIds.SETOFF_CARRIED_HOUSE_PROPERTY, StepKind.SET_OFF)).extracting(TraceLine::out)
                .containsExactly(rupees("20000"));
    }

    @Test
    void evaluate_whenTheOldRegimeMeetsAnAttributablePart_usesItFirst() {
        // When — the same ledger, the old regime
        NodeResult result = run(2026, OLD, "0", "100000", "0", Map.of(TaxYearId.of(2025), carried("70000", "50000", "0")));

        // Then — 50,000 (the attributable part, first) then 20,000: 1,00,000 − 70,000
        assertThat(result.value().asMoney()).isEqualTo(Money.of("30000"));
        assertThat(lines(result, RuleIds.SETOFF_CARRIED_DISALLOWED_PART, StepKind.SET_OFF)).extracting(TraceLine::out)
                .containsExactly(rupees("50000"));
        assertThat(lines(result, RuleIds.SETOFF_CARRIED_HOUSE_PROPERTY, StepKind.SET_OFF)).extracting(TraceLine::out)
                .containsExactly(rupees("20000"));
    }

    @Test
    void evaluate_whenPartOfACarriedLossWasAbsorbedEarlier_usesOnlyWhatRemains() {
        // When — 70,000 carried, 50,000 attributable, 60,000 absorbed since: 10,000 remains, none attributable
        NodeResult result = run(2026, NEW, "0", "100000", "0", Map.of(TaxYearId.of(2024), carried("70000", "50000", "60000")));

        // Then — 10,000 set off; nothing destroyed
        assertThat(result.value().asMoney()).isEqualTo(Money.of("90000"));
        assertThat(lines(result, RuleIds.SETOFF_CARRIED_DISALLOWED_PART, StepKind.MEASURE)).isEmpty();
    }

    @Test
    void evaluate_whenTheLedgerCarriesALossFromThisYear_isRefused() {
        // When / Then
        assertThatThrownBy(() -> run(2026, OLD, "0", "100000", "0", Map.of(TaxYearId.of(2026), carried("1000", "0", "0"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("carried only to later years");
    }

    /** As {@link #run}, TY 2026-27 old regime, with capital-gains parts and no carried losses. */
    private static NodeResult runWithCapitalGains(String salaries, String houseProperty, String otherSources,
            Map<CapitalGainsPart, Money> parts) {
        TaxYearId taxYear = TaxYearId.of(2026);
        EngineInputs plain = NodeHarness.plainTaxpayer();
        EngineInputs inputs = new EngineInputs(taxYear, OLD, TemporalContext.forTaxYear(taxYear, LocalDate.of(2026, 9, 21)),
                plain.entered(), plain.assessee(), plain.family(), plain.ledgers(), plain.elections(),
                plain.capitalGains(), plain.filing());
        return NodeHarness.with(pack(2026)).regime(OLD).inputs(inputs)
                .given(NodeIds.SALARY_INCOME, Money.of(salaries))
                .given(NodeIds.HOUSEPROPERTY_INCOME, Money.of(houseProperty))
                .given(NodeIds.OTHERSOURCES_INCOME, Money.of(otherSources))
                .given(NodeIds.CAPITALGAINS_INCOME, new CapitalGainsHead(parts, Money.ZERO, Money.ZERO))
                .run(new SetOffNode());
    }

    @Test
    void evaluate_whenAHousePropertyLossMeetsOnlyCapitalGains_takesTheirPartsInOrder() {
        // When — a 60,000 house-property loss; short-term slab 30,000, long-term listed 50,000; nothing else
        NodeResult result = runWithCapitalGains("0", "-60000", "0", Map.of(SHORT_TERM_SLAB,
                Money.of("30000"), LONG_TERM_LISTED_EQUITY, Money.of("50000")));

        // Then — S2 s.109(1) "including 'Capital gains'": 30,000 slab, then 30,000 of the listed part; 20,000 left
        HeadsAfterSetOff heads = (HeadsAfterSetOff) result.value();
        assertThat(heads.capitalGains(SHORT_TERM_SLAB)).isEqualTo(Money.ZERO);
        assertThat(heads.capitalGains(LONG_TERM_LISTED_EQUITY)).isEqualTo(Money.of("20000"));
        assertThat(heads.total()).isEqualTo(Money.of("20000"));
        assertThat(lines(result, StepKind.SET_OFF)).hasSize(2).allSatisfy(line ->
                assertThat(line.limitedBy().orElseThrow()).contains("CAPITAL_GAINS").contains("OQ-32"));
    }

    @Test
    void evaluate_whenAnotherHeadHasIncome_setsOffAgainstItBeforeCapitalGains() {
        // When — the same, with other sources 40,000: capital gains are last in the declared order (D-243)
        NodeResult result = runWithCapitalGains("0", "-60000", "40000", Map.of(SHORT_TERM_SLAB,
                Money.of("30000"), LONG_TERM_LISTED_EQUITY, Money.of("50000")));

        // Then — 40,000 from other sources, 20,000 from the slab part; the listed part untouched
        HeadsAfterSetOff heads = (HeadsAfterSetOff) result.value();
        assertThat(heads.capitalGains(SHORT_TERM_SLAB)).isEqualTo(Money.of("10000"));
        assertThat(heads.capitalGains(LONG_TERM_LISTED_EQUITY)).isEqualTo(Money.of("50000"));
        assertThat(heads.total()).isEqualTo(Money.of("60000"));
    }

    /** TY {@code year}, with capital-gains parts and the capital losses carried in (D-247); the other heads nil. */
    private static NodeResult runWithCarriedCapitalLoss(int year, RegimeCode regime, Map<CapitalGainsPart, Money> parts,
            Map<TaxYearId, CarriedCapitalLoss> carried) {
        return runWithCarriedCapitalLoss(pack(year), regime, parts, carried);
    }

    private static NodeResult runWithCarriedCapitalLoss(RulePack pack, RegimeCode regime,
            Map<CapitalGainsPart, Money> parts, Map<TaxYearId, CarriedCapitalLoss> carried) {
        int year = pack.taxYear().startYear();
        TaxYearId taxYear = TaxYearId.of(year);
        EngineInputs plain = NodeHarness.plainTaxpayer();
        LedgerSnapshot empty = LedgerSnapshot.empty();
        LedgerSnapshot ledgers = new LedgerSnapshot(empty.leaveTravelByBlock(), empty.vrsClaimedIn(),
                empty.gratuityAlreadyAllowed(), empty.leaveEncashmentAlreadyAllowed(),
                empty.housePropertyLossCarriedForward(), empty.educationLoanInterestFirstYear(), carried);
        EngineInputs inputs = new EngineInputs(taxYear, regime,
                TemporalContext.forTaxYear(taxYear, LocalDate.of(year, 9, 21)), plain.entered(), plain.assessee(),
                plain.family(), ledgers, plain.elections(), plain.capitalGains(), plain.filing());
        return NodeHarness.with(pack).regime(regime).inputs(inputs)
                .given(NodeIds.SALARY_INCOME, Money.ZERO)
                .given(NodeIds.HOUSEPROPERTY_INCOME, Money.ZERO)
                .given(NodeIds.OTHERSOURCES_INCOME, Money.ZERO)
                .given(NodeIds.CAPITALGAINS_INCOME, new CapitalGainsHead(parts, Money.ZERO, Money.ZERO))
                .run(new SetOffNode());
    }

    /** TY {@code year}'s shipped pack with every version of {@code row} defined as {@code definition} instead. */
    private static RulePack withDefinition(int year, RuleId row, RuleDefinition definition) {
        RulePack shipped = pack(year);
        RuleRow rule = shipped.rules().get(row);
        List<RuleVersion> versions = rule.versions().stream()
                .map(v -> new RuleVersion(v.validity(), v.regimes(), v.cap(), v.relief(), v.rounding(),
                        Optional.of(definition), v.conditions(), v.provision1961(), v.provision2025(),
                        v.introducedBy(), v.legalStatus(), v.publicationStatus(), v.provenance()))
                .toList();
        Map<RuleId, RuleRow> rules = new HashMap<>(shipped.rules());
        rules.put(row, new RuleRow(rule.id(), rule.axis(), versions, rule.midYearCombination(),
                rule.midYearCombinationRule(), rule.prorationBasis(), rule.headProvenance()));
        return new RulePack(shipped.taxYear(), shipped.layers(), shipped.instruments(), rules, shipped.ruleSets(),
                shipped.components(), shipped.regimes(), shipped.disallowanceSets(), shipped.conditionSets());
    }

    private static CarriedCapitalLoss capitalLoss(String shortTerm, String longTerm, String longTermAbsorbed,
            boolean inTime) {
        return new CarriedCapitalLoss(Money.of(shortTerm), Money.of(longTerm), Money.ZERO, Money.of(longTermAbsorbed),
                inTime);
    }

    /** Case A's gains: short-term listed 1,00,000, long-term listed 3,00,000. */
    private static Map<CapitalGainsPart, Money> caseAGains() {
        return Map.of(SHORT_TERM_LISTED_EQUITY, Money.of("100000"),
                LONG_TERM_LISTED_EQUITY, Money.of("300000"));
    }

    /** Case A's ledger, by the first of its two years of origin: long-term 2,00,000 (50,000 absorbed) and short-term
     * 30,000 from the first, short-term 40,000 from the next. */
    private static Map<TaxYearId, CarriedCapitalLoss> caseALedger(int firstYear, boolean secondInTime) {
        return Map.of(TaxYearId.of(firstYear), capitalLoss("30000", "200000", "50000", true),
                TaxYearId.of(firstYear + 1), capitalLoss("40000", "0", "0", secondInTime));
    }

    private static void assertCaseA(NodeResult result) {
        // long-term 1,50,000 off long-term listed → 1,50,000; short-term 30,000 then 40,000 off short-term listed
        // (the slab part being nil) → 30,000; the head down 2,20,000 from 4,00,000
        HeadsAfterSetOff heads = (HeadsAfterSetOff) result.value();
        assertThat(heads.capitalGains(SHORT_TERM_LISTED_EQUITY)).isEqualTo(Money.of("30000"));
        assertThat(heads.capitalGains(LONG_TERM_LISTED_EQUITY)).isEqualTo(Money.of("150000"));
        assertThat(heads.total()).isEqualTo(Money.of("180000"));
        assertThat(lines(result, RuleIds.SETOFF_CARRIED_CAPITAL_GAINS, StepKind.SET_OFF))
                .extracting(TraceLine::out)
                .containsExactly(rupees("150000"), rupees("30000"), rupees("40000"));
        assertThat(lines(result, RuleIds.SETOFF_CARRIED_CAPITAL_GAINS, StepKind.MEASURE)).isEmpty();
    }

    @Test
    void evaluate_whenCapitalLossesAreCarriedFromTwoYears_setsTheOldestOffFirstLongTermBeforeShort() {
        // When — A: TY 2026-27, old regime, losses of TY 2023-24 and TY 2024-25
        NodeResult result = runWithCarriedCapitalLoss(2026, OLD, caseAGains(), caseALedger(2023, true));

        // Then
        assertCaseA(result);
    }

    @Test
    void evaluate_whenTaxYear2025CarriesTheSameLosses_setsThemOffUnderSection74() {
        // When — E: A's figures in TY 2025-26, losses of TY 2022-23 and TY 2023-24
        NodeResult result = runWithCarriedCapitalLoss(2025, OLD, caseAGains(), caseALedger(2022, true));

        // Then
        assertCaseA(result);
        assertThat(lines(result, RuleIds.SETOFF_CARRIED_CAPITAL_GAINS, StepKind.SET_OFF).getFirst().provenance()
                .sourceDescription()).contains("s.74");
    }

    @Test
    void evaluate_whenTheNewRegimeMeetsACarriedCapitalLoss_setsItOffAsTheOldDoes() {
        // When — F: A in the new regime; a capital loss is not attributable to a disallowed deduction
        NodeResult result = runWithCarriedCapitalLoss(2026, NEW, caseAGains(), caseALedger(2023, true));

        // Then
        assertCaseA(result);
    }

    @Test
    void evaluate_whenACapitalLossIsInItsNinthYear_hasExpiredButTheEighthIsUsed() {
        // When — B: TY 2026-27; 10,000 short-term from TY 2018-19 (the eighth year after) and from TY 2017-18
        NodeResult result = runWithCarriedCapitalLoss(2026, OLD,
                Map.of(LONG_TERM_LISTED_EQUITY, Money.of("100000")),
                Map.of(TaxYearId.of(2017), capitalLoss("10000", "0", "0", true),
                        TaxYearId.of(2018), capitalLoss("10000", "0", "0", true)));

        // Then — only TY 2018-19's is set off
        HeadsAfterSetOff heads = (HeadsAfterSetOff) result.value();
        assertThat(heads.capitalGains(LONG_TERM_LISTED_EQUITY)).isEqualTo(Money.of("90000"));
        assertThat(lines(result, RuleIds.SETOFF_CARRIED_CAPITAL_GAINS, StepKind.MEASURE)).singleElement()
                .satisfies(line -> assertThat(line.limitedBy().orElseThrow()).contains("2017-18").contains("expired"));
    }

    @Test
    void evaluate_whenOnlyShortTermGainsMeetACarriedLongTermLoss_carriesItAll() {
        // When — C: a long-term loss of 1,00,000 brought in; only short-term listed gains of 50,000
        NodeResult result = runWithCarriedCapitalLoss(2026, OLD,
                Map.of(SHORT_TERM_LISTED_EQUITY, Money.of("50000")),
                Map.of(TaxYearId.of(2024), capitalLoss("0", "100000", "0", true)));

        // Then — S2 s.111(1)(a)(ii): only against long-term gains
        HeadsAfterSetOff heads = (HeadsAfterSetOff) result.value();
        assertThat(heads.capitalGains(SHORT_TERM_LISTED_EQUITY)).isEqualTo(Money.of("50000"));
        assertThat(lines(result, RuleIds.SETOFF_CARRIED_CAPITAL_GAINS, StepKind.SET_OFF)).isEmpty();
        assertThat(lines(result, RuleIds.SETOFF_CARRIED_CAPITAL_GAINS, StepKind.MEASURE)).singleElement()
                .satisfies(line -> assertThat(line.out()).isEqualTo(rupees("100000")));
    }

    @Test
    void evaluate_whenTheDeclaredPartsLetALongTermLossReachAShortTermPart_setsTheCarriedLossOffAgainstIt() {
        // Given — case C, but CG-PARTS declares that a long-term loss may reach the short-term listed part (D-309)
        RulePack pack = withDefinition(2026, RuleIds.CG_PARTS, new CapitalGainsParts(List.of(
                new CapitalGainsParts.Declared(SHORT_TERM_SLAB, "SHORT_TERM", false),
                new CapitalGainsParts.Declared(SHORT_TERM_LISTED_EQUITY, "SHORT_TERM", true),
                new CapitalGainsParts.Declared(LONG_TERM_OTHER, "LONG_TERM", true),
                new CapitalGainsParts.Declared(LONG_TERM_LISTED_EQUITY, "LONG_TERM", true))));

        // When — a long-term loss of 1,00,000 brought in; only short-term listed gains of 50,000
        NodeResult result = runWithCarriedCapitalLoss(pack, OLD,
                Map.of(SHORT_TERM_LISTED_EQUITY, Money.of("50000")),
                Map.of(TaxYearId.of(2024), capitalLoss("0", "100000", "0", true)));

        // Then — the row, not the part, says what the loss reaches: 50,000 − 50,000 = 0; 1,00,000 − 50,000 carried
        HeadsAfterSetOff heads = (HeadsAfterSetOff) result.value();
        assertThat(heads.capitalGains(SHORT_TERM_LISTED_EQUITY)).isEqualTo(Money.ZERO);
        assertThat(lines(result, RuleIds.SETOFF_CARRIED_CAPITAL_GAINS, StepKind.SET_OFF)).singleElement()
                .satisfies(line -> assertThat(line.out()).isEqualTo(rupees("50000")));
        assertThat(lines(result, RuleIds.SETOFF_CARRIED_CAPITAL_GAINS, StepKind.MEASURE)).singleElement()
                .satisfies(line -> assertThat(line.out()).isEqualTo(rupees("50000")));
    }

    @Test
    void evaluate_whenALossWasNotDeterminedInAReturnFiledInTime_doesNotCarryIt() {
        // When — D: A's ledger with TY 2024-25's loss from a late return
        NodeResult result = runWithCarriedCapitalLoss(2026, OLD, caseAGains(), caseALedger(2023, false));

        // Then — its 40,000 is not set off: short-term listed 70,000; the line names s.121
        HeadsAfterSetOff heads = (HeadsAfterSetOff) result.value();
        assertThat(heads.capitalGains(SHORT_TERM_LISTED_EQUITY)).isEqualTo(Money.of("70000"));
        assertThat(lines(result, RuleIds.SETOFF_CARRIED_CAPITAL_GAINS, StepKind.MEASURE)).singleElement()
                .satisfies(line -> assertThat(line.limitedBy().orElseThrow()).contains("s.121"));
    }

    @Test
    void evaluate_whenTheRowDoesNotRequireAReturnFiledInTime_carriesALateLoss() {
        // Given — the capital-loss row stating no return condition
        RulePack pack = withDefinition(2026, RuleIds.SETOFF_CARRIED_CAPITAL_GAINS,
                new LossCarryForward(HeadCode.CAPITAL_GAINS, new TaxYearSpan(8), false));

        // When — D's ledger: TY 2024-25's loss from a late return
        NodeResult result = runWithCarriedCapitalLoss(pack, OLD, caseAGains(), caseALedger(2023, false));

        // Then — set off as if in time: case A's figures
        assertCaseA(result);
    }

    @Test
    void evaluate_whenTheHousePropertyRowRequiresAReturnFiledInTime_isRefused() {
        // Given
        RulePack pack = withDefinition(2026, RuleIds.SETOFF_CARRIED_HOUSE_PROPERTY,
                new LossCarryForward(HeadCode.HOUSE_PROPERTY, new TaxYearSpan(8), true));

        // When / Then — the house-property ledger states no return fact to test
        assertThatThrownBy(() -> runWithCarriedCapitalLoss(pack, OLD, caseAGains(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("return filed in time");
    }

    @Test
    void evaluate_whenTheHousePropertyRowNamesCapitalGains_isRefused() {
        // Given
        RulePack pack = withDefinition(2026, RuleIds.SETOFF_CARRIED_HOUSE_PROPERTY,
                new LossCarryForward(HeadCode.CAPITAL_GAINS, new TaxYearSpan(8), false));

        // When / Then
        assertThatThrownBy(() -> runWithCarriedCapitalLoss(pack, OLD, caseAGains(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not the capital-gains parts");
    }

    @Test
    void evaluate_whenTheCapitalLossRowNamesAnotherHead_isRefused() {
        // Given
        RulePack pack = withDefinition(2026, RuleIds.SETOFF_CARRIED_CAPITAL_GAINS,
                new LossCarryForward(HeadCode.HOUSE_PROPERTY, new TaxYearSpan(8), true));

        // When / Then
        assertThatThrownBy(() -> runWithCarriedCapitalLoss(pack, OLD, caseAGains(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("works on the capital-gains parts");
    }

    @Test
    void evaluate_whenTheLedgerCarriesACapitalLossFromThisYear_isRefused() {
        // When / Then
        assertThatThrownBy(() -> runWithCarriedCapitalLoss(2026, OLD, caseAGains(),
                Map.of(TaxYearId.of(2026), capitalLoss("1000", "0", "0", true))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("carried only to later years");
    }
}
