package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.thiru.wealthlens.taxengine.input.Assessee.ResidentialStatus;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.Individual;
import com.thiru.wealthlens.taxengine.pipeline.HeadsAfterSetOff;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.SpecialRateId;
import com.thiru.wealthlens.taxengine.pipeline.TaxByBasis;
import com.thiru.wealthlens.taxengine.rules.condition.AgeAtLeast;
import com.thiru.wealthlens.taxengine.rules.condition.AgeSubject;
import com.thiru.wealthlens.taxengine.rules.condition.IsResident;
import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsPart;
import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsParts;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinition;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.MidYearCombination;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.registry.GoverningInstruments;
import com.thiru.wealthlens.taxengine.rules.registry.Instrument;
import com.thiru.wealthlens.taxengine.rules.registry.VerificationLevel;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.rules.temporal.Validity;
import com.thiru.wealthlens.taxengine.rules.temporal.proration.Days;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.test.LockedInstruments;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.net.URI;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tax at slab rates on the rounded total income, from the regime's own ladder in the shipped pack
 * ({@code DR 16} S2 step 5; {@code DR 23} A11). Every expected figure is worked by hand in
 * {@code plans/W-046.md}, not taken from a run.
 */
class TaxByBasisNodeTest {

    private static final CapitalGainsPart SHORT_TERM_LISTED_EQUITY = new CapitalGainsPart("SHORT_TERM_LISTED_EQUITY");

    private static final CapitalGainsPart LONG_TERM_OTHER = new CapitalGainsPart("LONG_TERM_OTHER");

    private static final CapitalGainsPart LONG_TERM_LISTED_EQUITY = new CapitalGainsPart("LONG_TERM_LISTED_EQUITY");

    private static final RegimeCode NEW_TAX_REGIME = new RegimeCode("NEW_TAX_REGIME");
    private static final RegimeCode FIRST_SCHEDULE_RATES = new RegimeCode("FIRST_SCHEDULE_RATES");

    private static RulePack pack() {
        return new ClasspathRulePackProvider(
                        "rulepacks", Set.of(TaxYearId.of(2026)), new JsonPackAssembler(instruments()))
                .load(TaxYearId.of(2026));
    }

    private static GoverningInstruments instruments() {
        Instrument act = Instrument.assented(
                "Income-tax Act, 2025",
                LocalDate.of(2025, 8, 21),
                LocalDate.of(2026, 4, 1),
                "CG-DL-E-22082025-265620",
                URI.create("https://egazette.gov.in/WriteReadData/2025/265620.pdf"),
                VerificationLevel.SOURCE_CHECKED);
        Instrument rules = Instrument.notified(
                "Income-tax Rules, 2026",
                LocalDate.of(2026, 4, 1),
                "CG-DL-E-20032026-271092",
                URI.create("https://egazette.gov.in/WriteReadData/2026/271092.pdf"),
                VerificationLevel.SOURCE_CHECKED);
        return new GoverningInstruments(act, act, rules);
    }

    private static NodeResult run(RegimeCode regime, String roundedTotalIncome) {
        return NodeHarness.with(pack())
                .regime(regime)
                .given(NodeIds.ROUND_TOTALINCOME, Money.of(roundedTotalIncome))
                .given(NodeIds.SETOFF, new HeadsAfterSetOff(Money.ZERO, Map.of()))
                .run(new TaxByBasisNode());
    }

    @Test
    void evaluate_whenNewRegimeIncomeIsTwelveLakh_isSixtyThousandAtSlabRates() {
        // Given — 4L–8L at 5% = 20,000; 8L–12L at 10% = 40,000 (DR 23 A11)
        // When
        NodeResult result = run(NEW_TAX_REGIME, "1200000");

        // Then
        assertThat(result.value()).isEqualTo(TaxByBasis.atSlabRatesOnly(Money.of("60000")));
    }

    @Test
    void evaluate_whenNewRegimeIncomeIsAtTheTopOfTheNilBand_isZero() {
        // When
        NodeResult result = run(NEW_TAX_REGIME, "400000");

        // Then
        assertThat(result.value()).isEqualTo(TaxByBasis.atSlabRatesOnly(Money.ZERO));
    }

    @Test
    void evaluate_whenNewRegimeIncomeIsOneRupeeIntoTheTopBand_chargesThirtyPaiseAndDoesNotRound() {
        // Given — 20,000 + 40,000 + 60,000 + 80,000 + 1,00,000 = 3,00,000; 30% of ₹1 = 0.30;
        // rounding is not this node's (RATE-ROUND-003)
        // When
        NodeResult result = run(NEW_TAX_REGIME, "2400001");

        // Then
        assertThat(result.value()).isEqualTo(TaxByBasis.atSlabRatesOnly(Money.of("300000.30")));
    }

    @Test
    void evaluate_whenFirstScheduleRatesIncomeIsTenLakh_usesThatRegimesLadder() {
        // Given — 2.5L–5L at 5% = 12,500; 5L–10L at 20% = 1,00,000; FA 2026 First Schedule
        // Part I-B Para A (I) states the same "₹ 112500" at ₹10,00,000
        // When
        NodeResult result = run(FIRST_SCHEDULE_RATES, "1000000");

        // Then
        assertThat(result.value()).isEqualTo(TaxByBasis.atSlabRatesOnly(Money.of("112500")));
        assertThat(result.trace()).singleElement().extracting(TraceLine::rule)
                .isEqualTo(RuleIds.RATE_SLAB_FIRST_SCHEDULE);
    }

    @Test
    void evaluate_whenRun_explainsItselfInOneTaxLineNamingTheRegimesRuleAndItsProvenance() {
        // Given
        RulePack pack = pack();

        // When
        NodeResult result = run(NEW_TAX_REGIME, "1200000");

        // Then
        TraceLine line = result.trace().getFirst();
        assertThat(result.trace()).hasSize(1);
        assertThat(line.rule()).isEqualTo(RuleIds.RATE_SLAB_NEW_TAX_REGIME);
        assertThat(line.kind()).isEqualTo(StepKind.TAX);
        assertThat(line.node()).isEqualTo(NodeIds.TAX_BYBASIS);
        assertThat(line.provenance()).isEqualTo(pack.rule(RuleIds.RATE_SLAB_NEW_TAX_REGIME)
                .versionAt(NodeHarness.plainTaxpayer().temporal()).provenance());
    }

    // -- age ladders (W-046b, D-072); tax year 2026-27 ends 31-3-2027 -------------------------

    private static EngineInputs person(LocalDate dateOfBirth, ResidentialStatus residence) {
        EngineInputs plain = NodeHarness.plainTaxpayer();
        return new EngineInputs(plain.taxYear(), plain.regime(), plain.temporal(), plain.entered(),
                new Individual(dateOfBirth, residence, Optional.empty(), Optional.empty()),
                plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(), plain.filing());
    }

    private static NodeResult runFor(RulePack pack, RegimeCode regime, String income, EngineInputs inputs) {
        return NodeHarness.with(pack)
                .regime(regime)
                .inputs(inputs)
                .given(NodeIds.ROUND_TOTALINCOME, Money.of(income))
                .given(NodeIds.SETOFF, new HeadsAfterSetOff(Money.ZERO, Map.of()))
                .run(new TaxByBasisNode());
    }

    @Test
    void evaluate_whenAResidentIsSixtyFive_usesTheSixtyToSeventyNineLadder() {
        // Given — 3L–5L at 5% = 10,000; 5L–10L at 20% = 1,00,000; FA 2026 First Schedule Part I-B
        // Para A (II) states "₹ 110000" at ₹10,00,000
        // When
        NodeResult result = runFor(pack(), FIRST_SCHEDULE_RATES, "1000000",
                person(LocalDate.of(1961, 6, 1), ResidentialStatus.RESIDENT));

        // Then
        assertThat(result.value()).isEqualTo(TaxByBasis.atSlabRatesOnly(Money.of("110000")));
        assertThat(result.trace()).singleElement().extracting(TraceLine::rule)
                .isEqualTo(RuleIds.RATE_SLAB_FIRST_SCHEDULE_AGE_60TO79);
    }

    @Test
    void evaluate_whenAResidentIsEightyFive_usesTheEightyPlusLadder() {
        // Given — 5L–10L at 20% = 1,00,000 (Para A (III))
        // When
        NodeResult result = runFor(pack(), FIRST_SCHEDULE_RATES, "1000000",
                person(LocalDate.of(1941, 6, 1), ResidentialStatus.RESIDENT));

        // Then
        assertThat(result.value()).isEqualTo(TaxByBasis.atSlabRatesOnly(Money.of("100000")));
        assertThat(result.trace()).singleElement().extracting(TraceLine::rule)
                .isEqualTo(RuleIds.RATE_SLAB_FIRST_SCHEDULE_AGE_80);
    }

    @Test
    void evaluate_whenANonResidentIsSixtyFive_usesTheUnderSixtyLadder() {
        // Given — the senior ladders are for "an individual, being a resident in India"
        // When
        NodeResult result = runFor(pack(), FIRST_SCHEDULE_RATES, "1000000",
                person(LocalDate.of(1961, 6, 1), ResidentialStatus.NON_RESIDENT));

        // Then
        assertThat(result.value()).isEqualTo(TaxByBasis.atSlabRatesOnly(Money.of("112500")));
    }

    @Test
    void evaluate_whenANotOrdinarilyResidentIsSixtyFive_isTreatedAsResident() {
        // Given — S2 s.5(1)(c) treats a "not ordinarily resident" person within "a person, who is a resident"
        // When
        NodeResult result = runFor(pack(), FIRST_SCHEDULE_RATES, "1000000",
                person(LocalDate.of(1961, 6, 1), ResidentialStatus.RNOR));

        // Then
        assertThat(result.value()).isEqualTo(TaxByBasis.atSlabRatesOnly(Money.of("110000")));
    }

    @Test
    void evaluate_whenAResidentTurnsSixtyDuringTheYear_usesTheSeniorLadderForTheWholeYear() {
        // Given — "at any time during the tax year": 60 on 15-11-2026, inside 2026-27
        // When
        NodeResult result = runFor(pack(), FIRST_SCHEDULE_RATES, "1000000",
                person(LocalDate.of(1966, 11, 15), ResidentialStatus.RESIDENT));

        // Then
        assertThat(result.value()).isEqualTo(TaxByBasis.atSlabRatesOnly(Money.of("110000")));
    }

    @Test
    void evaluate_whenBornOnTheFirstOfApril_turnsSixtyOnlyAfterTheYearByCalendarReckoning() {
        // Given — born 1-4-1967, 59 on 31-3-2027. UNVERIFIED edge (D-072): a doctrine that an age is
        // attained the day before the anniversary would make this 60; recorded for the OQ-1 reviewer
        // When
        NodeResult result = runFor(pack(), FIRST_SCHEDULE_RATES, "1000000",
                person(LocalDate.of(1967, 4, 1), ResidentialStatus.RESIDENT));

        // Then
        assertThat(result.value()).isEqualTo(TaxByBasis.atSlabRatesOnly(Money.of("112500")));
    }

    @Test
    void evaluate_whenANewRegimeResidentIsSixtyFive_hasNoAgeVariant() {
        // Given — RATE-SLAB-AGE-002: the new regime's table has no age variants
        // When
        NodeResult result = runFor(pack(), NEW_TAX_REGIME, "1200000",
                person(LocalDate.of(1961, 6, 1), ResidentialStatus.RESIDENT));

        // Then
        assertThat(result.value()).isEqualTo(TaxByBasis.atSlabRatesOnly(Money.of("60000")));
    }

    @Test
    void evaluate_whenTwoConditionalLaddersBothHold_isRefusedNamingBoth() {
        // Given — the 80+ ladder rewritten to hold from 60, so it overlaps 60–79 for someone of 65
        RulePack pack = pack();
        RuleRow eighty = pack.rule(RuleIds.RATE_SLAB_FIRST_SCHEDULE_AGE_80);
        RuleVersion v = eighty.versions().getFirst();
        RuleVersion overlapping = new RuleVersion(v.validity(), v.regimes(), v.cap(), v.relief(), v.rounding(), v.definition(),
                List.of(new IsResident(), new AgeAtLeast(60, AgeSubject.SELF)), v.provision1961(), v.provision2025(),
                v.introducedBy(), v.legalStatus(), v.publicationStatus(), v.provenance());
        Map<RuleId, RuleRow> rules = new HashMap<>(pack.rules());
        rules.put(eighty.id(), new RuleRow(eighty.id(), eighty.axis(), List.of(overlapping),
                eighty.midYearCombination(), eighty.midYearCombinationRule(), eighty.prorationBasis(), eighty.headProvenance()));
        RulePack overlap = new RulePack(pack.taxYear(), pack.layers(), pack.instruments(), rules, pack.ruleSets(),
                pack.components(), pack.regimes(), pack.disallowanceSets(), pack.conditionSets());

        // When / Then
        assertThatThrownBy(() -> runFor(overlap, FIRST_SCHEDULE_RATES, "1000000",
                        person(LocalDate.of(1961, 6, 1), ResidentialStatus.RESIDENT)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(RuleIds.RATE_SLAB_FIRST_SCHEDULE_AGE_60TO79.value())
                .hasMessageContaining(RuleIds.RATE_SLAB_FIRST_SCHEDULE_AGE_80.value());
    }

    @Test
    void evaluate_whenTheLadderChangesMidYearAndIsProrated_isRefusedBecauseNoOneLadderGoverns() {
        // Given — D-098: the new regime's ladder split at 1 Oct 2026 and declared PRORATE_PER_SEGMENT
        RulePack pack = pack();
        RuleRow ladder = pack.rule(RuleIds.RATE_SLAB_NEW_TAX_REGIME);
        RuleVersion v = ladder.versions().getFirst();
        RuleVersion first = new RuleVersion(
                new Validity.Between(LocalDate.of(2026, 4, 1), Optional.of(LocalDate.of(2026, 9, 30))), v.regimes(),
                v.cap(), v.relief(), v.rounding(), v.definition(), v.conditions(), v.provision1961(),
                v.provision2025(), v.introducedBy(), v.legalStatus(), v.publicationStatus(), v.provenance());
        RuleVersion second = new RuleVersion(Validity.Between.fromOnwards(LocalDate.of(2026, 10, 1)), v.regimes(),
                v.cap(), v.relief(), v.rounding(), v.definition(), v.conditions(), v.provision1961(),
                v.provision2025(), v.introducedBy(), v.legalStatus(), v.publicationStatus(), v.provenance());
        Map<RuleId, RuleRow> rules = new HashMap<>(pack.rules());
        rules.put(ladder.id(), new RuleRow(ladder.id(), ladder.axis(), List.of(first, second),
                MidYearCombination.PRORATE_PER_SEGMENT, null, Optional.of(new Days()), ladder.headProvenance()));
        RulePack split = new RulePack(pack.taxYear(), pack.layers(), pack.instruments(), rules, pack.ruleSets(),
                pack.components(), pack.regimes(), pack.disallowanceSets(), pack.conditionSets());

        // When / Then — a ladder's bands cannot be apportioned, so the node refuses rather than use year-end
        assertThatThrownBy(() -> NodeHarness.with(split)
                        .regime(NEW_TAX_REGIME)
                        .given(NodeIds.ROUND_TOTALINCOME, Money.of("1000000"))
                        .given(NodeIds.SETOFF, new HeadsAfterSetOff(Money.ZERO, Map.of()))
                        .run(new TaxByBasisNode()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(RuleIds.RATE_SLAB_NEW_TAX_REGIME.value())
                .hasMessageContaining("PRORATE_PER_SEGMENT");
    }

    @Test
    void run_whenRoundedTotalIncomeIsNotGiven_isRefusedBecauseTheNodeDeclaresIt() {
        // When / Then — the node reads only what it declares, and it declares the rounded income
        assertThatThrownBy(() -> NodeHarness.with(pack()).regime(NEW_TAX_REGIME).run(new TaxByBasisNode()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(NodeIds.ROUND_TOTALINCOME.value());
    }

    // Special-rate capital gains (D-244). Expected figures are worked by hand in plans/W-088b.md, cases A–C.

    private static final SpecialRateId SHORT_LISTED = new SpecialRateId("RATE_CG_SHORT_LISTED_EQUITY");
    private static final SpecialRateId LONG_OTHER = new SpecialRateId("RATE_CG_LONG_OTHER");
    private static final SpecialRateId LONG_LISTED = new SpecialRateId("RATE_CG_LONG_LISTED_EQUITY");

    private static NodeResult runWithGains(int year, RegimeCode regime, ResidentialStatus residence,
            String roundedTotalIncome, Map<CapitalGainsPart, Money> gains) {
        return runWithGains(shippedPack(year), regime, residence, roundedTotalIncome, gains);
    }

    private static RulePack shippedPack(int year) {
        TaxYearId taxYear = TaxYearId.of(year);
        return new ClasspathRulePackProvider(
                        "rulepacks", Set.of(taxYear), new JsonPackAssembler(LockedInstruments.of(taxYear)))
                .load(taxYear);
    }

    private static NodeResult runWithGains(RulePack pack, RegimeCode regime, ResidentialStatus residence,
            String roundedTotalIncome, Map<CapitalGainsPart, Money> gains) {
        TaxYearId taxYear = pack.taxYear();
        EngineInputs plain = NodeHarness.plainTaxpayer();
        Individual person = new Individual(taxYear.endsOn().minusYears(40).minusMonths(6), residence,
                Optional.empty(), Optional.empty());
        EngineInputs inputs = new EngineInputs(taxYear, regime,
                TemporalContext.forTaxYear(taxYear, taxYear.startsOn().plusMonths(5)), plain.entered(), person,
                plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(), plain.filing());
        return NodeHarness.with(pack).regime(regime).inputs(inputs)
                .given(NodeIds.ROUND_TOTALINCOME, Money.of(roundedTotalIncome))
                .given(NodeIds.SETOFF, new HeadsAfterSetOff(Money.of(roundedTotalIncome), gains))
                .run(new TaxByBasisNode());
    }

    private static Map<CapitalGainsPart, Money> caseAGains() {
        return Map.of(SHORT_TERM_LISTED_EQUITY, Money.of("50000"),
                LONG_TERM_OTHER, Money.of("80000"),
                LONG_TERM_LISTED_EQUITY, Money.of("200000"));
    }

    @Test
    void evaluate_whenAResidentsSlabIncomeLeavesTheBasicExemptionUnused_shiftsItOntoTheGainsInOrder() {
        // Given — A: TY 2025-26, old regime, resident aged 40; slab income 1,50,000 + s.111A 50,000 + s.112 80,000
        // + s.112A 2,00,000 = 4,80,000. Basic exemption 2,50,000 − 1,50,000 = 1,00,000 unused: s.111A 50,000 → nil,
        // s.112 80,000 → 30,000. Tax: slab 0; s.111A 0; s.112 30,000 × 12.5% = 3,750;
        // s.112A (2,00,000 − 1,25,000) × 12.5% = 9,375
        // When
        NodeResult result = runWithGains(2025, FIRST_SCHEDULE_RATES, ResidentialStatus.RESIDENT, "480000",
                caseAGains());

        // Then
        assertThat(result.value()).isEqualTo(new TaxByBasis(Money.ZERO, Map.of(SHORT_LISTED, Money.ZERO,
                LONG_OTHER, Money.of("3750"), LONG_LISTED, Money.of("9375"))));
        assertThat(result.trace()).filteredOn(line -> line.kind() == StepKind.MEASURE)
                .extracting(TraceLine::rule, TraceLine::in, TraceLine::out)
                .containsExactly(
                        tuple(RuleIds.RATE_CG_SHORTFALL_ORDER,
                                MoneyValue.of(Money.of("50000")), MoneyValue.of(Money.ZERO)),
                        tuple(RuleIds.RATE_CG_SHORTFALL_ORDER,
                                MoneyValue.of(Money.of("80000")), MoneyValue.of(Money.of("30000"))));
    }

    /** TY 2026-27's shipped pack with every version of {@code CG-PARTS} declaring the parts in {@code order} (D-309). */
    private static RulePack withPartsInOrder(CapitalGainsPart... order) {
        RulePack shipped = shippedPack(2026);
        RuleRow rule = shipped.rules().get(RuleIds.CG_PARTS);
        List<RuleVersion> versions = rule.versions().stream()
                .map(v -> new RuleVersion(v.validity(), v.regimes(), v.cap(), v.relief(), v.rounding(),
                        Optional.of(reordered((CapitalGainsParts) v.definition().orElseThrow(), order)),
                        v.conditions(), v.provision1961(), v.provision2025(), v.introducedBy(), v.legalStatus(),
                        v.publicationStatus(), v.provenance()))
                .toList();
        Map<RuleId, RuleRow> rules = new HashMap<>(shipped.rules());
        rules.put(rule.id(), new RuleRow(rule.id(), rule.axis(), versions, rule.midYearCombination(),
                rule.midYearCombinationRule(), rule.prorationBasis(), rule.headProvenance()));
        return new RulePack(shipped.taxYear(), shipped.layers(), shipped.instruments(), rules, shipped.ruleSets(),
                shipped.components(), shipped.regimes(), shipped.disallowanceSets(), shipped.conditionSets());
    }

    private static RuleDefinition reordered(CapitalGainsParts declared, CapitalGainsPart... order) {
        return new CapitalGainsParts(List.of(order).stream()
                .map(part -> declared.of(part).orElseThrow()).toList());
    }

    /** The special-rate tax lines' rows, in the order the node wrote them (the first tax line is the slab ladder's). */
    private static List<RuleId> specialRateRows(NodeResult result) {
        List<RuleId> rows = result.trace().stream().filter(line -> line.kind() == StepKind.TAX)
                .map(TraceLine::rule).toList();
        return rows.subList(1, rows.size());
    }

    @Test
    void evaluate_whenThePartsAreDeclaredInTheirOrder_writesTheSpecialRateLinesInThatOrder() {
        // Given — A's three special-rate gains; a non-resident, so no shortfall shifts them
        // When
        NodeResult result = runWithGains(shippedPack(2026), FIRST_SCHEDULE_RATES, ResidentialStatus.NON_RESIDENT,
                "480000", caseAGains());

        // Then — CG-PARTS' order: short-term listed, long-term other, long-term listed
        assertThat(specialRateRows(result)).containsExactly(RuleIds.RATE_CG_SHORT_LISTED_EQUITY,
                RuleIds.RATE_CG_LONG_OTHER, RuleIds.RATE_CG_LONG_LISTED_EQUITY);
    }

    @Test
    void evaluate_whenThePartsAreDeclaredInAnotherOrder_writesTheSpecialRateLinesInThatOrderInstead() {
        // Given — the same gains, CG-PARTS declaring the parts the other way round
        RulePack pack = withPartsInOrder(LONG_TERM_LISTED_EQUITY, LONG_TERM_OTHER, SHORT_TERM_LISTED_EQUITY,
                new CapitalGainsPart("SHORT_TERM_SLAB"));

        // When
        NodeResult result = runWithGains(pack, FIRST_SCHEDULE_RATES, ResidentialStatus.NON_RESIDENT, "480000",
                caseAGains());

        // Then — the order is the row's, not the head's map's
        assertThat(specialRateRows(result)).containsExactly(RuleIds.RATE_CG_LONG_LISTED_EQUITY,
                RuleIds.RATE_CG_LONG_OTHER, RuleIds.RATE_CG_SHORT_LISTED_EQUITY);
    }

    @Test
    void evaluate_whenAPartNoSpecialRateRowNames_chargesItWithTotalIncomeAtSlabRates() {
        // Given — a part code no Java names and no RS-RATE-CG-SPECIAL row charges (D-309); TY 2026-27, new regime,
        // total income 12,00,000 of which 3,00,000 is that part: 4L–8L at 5% = 20,000; 8L–12L at 10% = 40,000
        // When
        NodeResult result = runWithGains(2026, NEW_TAX_REGIME, ResidentialStatus.RESIDENT, "1200000",
                Map.of(new CapitalGainsPart("SHORT_TERM_OTHER_RATE"), Money.of("300000")));

        // Then — no special-rate line; the whole of total income at the slab rates
        assertThat(result.value()).isEqualTo(TaxByBasis.atSlabRatesOnly(Money.of("60000")));
    }

    @Test
    void evaluate_whenTheAssesseeIsNonResident_shiftsNothing() {
        // Given — B: case A's figures, non-resident: s.111A 50,000 × 20% = 10,000; s.112 80,000 × 12.5% = 10,000;
        // s.112A 9,375; slab income 1,50,000 within the nil band
        // When
        NodeResult result = runWithGains(2025, FIRST_SCHEDULE_RATES, ResidentialStatus.NON_RESIDENT, "480000",
                caseAGains());

        // Then
        assertThat(result.value()).isEqualTo(new TaxByBasis(Money.ZERO, Map.of(SHORT_LISTED, Money.of("10000"),
                LONG_OTHER, Money.of("10000"), LONG_LISTED, Money.of("9375"))));
        assertThat(result.trace()).noneMatch(line -> line.kind() == StepKind.MEASURE);
    }

    @Test
    void evaluate_whenT13Dash1sFactsAreEntered_chargesTheListedLongTermGainsAfterTheShiftAndTheExemption() {
        // Given — C: TY 2026-27, new regime, resident; slab income 1,00,000, s.198 gains 11,00,000.
        // 4,00,000 − 1,00,000 = 3,00,000 shifted → 8,00,000; (8,00,000 − 1,25,000) × 12.5% = 84,375 (OQ-33's
        // default: the 1,25,000 survives the shift)
        // When
        NodeResult result = runWithGains(2026, NEW_TAX_REGIME, ResidentialStatus.RESIDENT, "1200000",
                Map.of(LONG_TERM_LISTED_EQUITY, Money.of("1100000")));

        // Then
        assertThat(result.value()).isEqualTo(new TaxByBasis(Money.ZERO, Map.of(LONG_LISTED, Money.of("84375"))));
        assertThat(result.trace().getFirst().in()).isEqualTo(MoneyValue.of(Money.of("100000")));
    }
}
