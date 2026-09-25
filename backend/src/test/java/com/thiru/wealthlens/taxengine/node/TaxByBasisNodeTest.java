package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.Assessee.ResidentialStatus;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.Individual;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.TaxByBasis;
import com.thiru.wealthlens.taxengine.rules.condition.AgeAtLeast;
import com.thiru.wealthlens.taxengine.rules.condition.AgeSubject;
import com.thiru.wealthlens.taxengine.rules.condition.IsResident;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.registry.GoverningInstruments;
import com.thiru.wealthlens.taxengine.rules.registry.Instrument;
import com.thiru.wealthlens.taxengine.rules.registry.VerificationLevel;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
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
        RuleVersion overlapping = new RuleVersion(v.validity(), v.regimes(), v.cap(), v.relief(),
                List.of(new IsResident(), new AgeAtLeast(60, AgeSubject.SELF)), v.provision1961(), v.provision2025(),
                v.introducedBy(), v.legalStatus(), v.publicationStatus(), v.provenance());
        Map<RuleId, RuleRow> rules = new HashMap<>(pack.rules());
        rules.put(eighty.id(), new RuleRow(eighty.id(), eighty.axis(), List.of(overlapping),
                eighty.midYearCombination(), eighty.midYearCombinationRule(), eighty.headProvenance()));
        RulePack overlap = new RulePack(pack.taxYear(), pack.layers(), pack.instruments(), rules,
                pack.components(), pack.regimes(), pack.disallowanceSets(), pack.conditionSets());

        // When / Then
        assertThatThrownBy(() -> runFor(overlap, FIRST_SCHEDULE_RATES, "1000000",
                        person(LocalDate.of(1961, 6, 1), ResidentialStatus.RESIDENT)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(RuleIds.RATE_SLAB_FIRST_SCHEDULE_AGE_60TO79.value())
                .hasMessageContaining(RuleIds.RATE_SLAB_FIRST_SCHEDULE_AGE_80.value());
    }

    @Test
    void run_whenRoundedTotalIncomeIsNotGiven_isRefusedBecauseTheNodeDeclaresIt() {
        // When / Then — the node reads only what it declares, and it declares the rounded income
        assertThatThrownBy(() -> NodeHarness.with(pack()).regime(NEW_TAX_REGIME).run(new TaxByBasisNode()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(NodeIds.ROUND_TOTALINCOME.value());
    }
}
