package com.thiru.wealthlens.taxplanning.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.input.CapitalGainsInput;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.BucketFacts;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.SideVenue;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.node.CapitalGainsNode;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.test.CapitalGainsHarness;
import com.thiru.wealthlens.taxengine.test.LockedInstruments;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import com.thiru.wealthlens.taxengine.trace.Notice;
import com.thiru.wealthlens.taxengine.trace.Severity;
import com.thiru.wealthlens.taxengine.trace.TraceAnnotation;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * The notices a land or building sale carries for the provisions the engine does not build yet (W-088g4, D-432): the
 * stamp-duty value as the consideration (2025 s.78; 1961 s.50C), which can raise the tax, and the cost of improvement and
 * the 1-4-2001 value (2025 s.72(1)(b), s.90; 1961 s.48, s.55), which can only lower it. Which buckets each reaches is
 * {@code RS-CG-NOT-MODELLED}'s; a notice moves no number.
 */
class LandSaleNoticeTest {

    private static final RuleId STAMP_DUTY = new RuleId("CG-NOT-MODELLED-STAMPDUTY");

    private static final RuleId IMPROVEMENT = new RuleId("CG-NOT-MODELLED-IMPROVEMENT");

    private static final RuleId VALUE_2001 = new RuleId("CG-NOT-MODELLED-VALUE2001");

    private static final RuleId LAND_TERM = new RuleId("CG-TERM-LAND-BUILDING");

    private static final LocalDate SOLD_2026 = LocalDate.of(2026, 9, 1);

    private static RulePack pack(TaxYearId taxYear) {
        return new ClasspathRulePackProvider("rulepacks", Set.of(taxYear),
                new JsonPackAssembler(LockedInstruments.of(taxYear))).load(taxYear);
    }

    private static CapitalGainsInput.Bucket land(LocalDate bought, LocalDate sold) {
        BucketFacts facts = new BucketFacts("LAND_OR_BUILDING", "UNKNOWN", "UNKNOWN", "PURCHASE", Optional.empty(),
                bought, sold, SideVenue.UNRECORDED, SideVenue.UNRECORDED);
        return new CapitalGainsInput.Bucket(Money.of("4000000"), Money.of("1000000"), List.of(), facts,
                Optional.empty());
    }

    private static NodeResult run(int year, CapitalGainsInput.Bucket bucket) {
        TaxYearId taxYear = TaxYearId.of(year);
        EngineInputs plain = NodeHarness.plainTaxpayer();
        EngineInputs inputs = new EngineInputs(taxYear, plain.regime(),
                TemporalContext.forTaxYear(taxYear, taxYear.startsOn().plusMonths(5)), plain.entered(),
                plain.assessee(), plain.family(), plain.ledgers(), plain.elections(),
                CapitalGainsHarness.inputOf(bucket), plain.filing());
        return NodeHarness.with(pack(taxYear)).regime(plain.regime()).inputs(inputs).run(new CapitalGainsNode());
    }

    /** Each notice's rule and severity. */
    private static Map<RuleId, Severity> severities(NodeResult result) {
        return result.annotations().stream()
                .flatMap(annotation -> annotation.notices().stream())
                .collect(Collectors.toMap(notice -> notice.relatedRules().getFirst(), Notice::severity));
    }

    private static int termLine(NodeResult result) {
        return result.trace().stream().filter(line -> line.rule().equals(LAND_TERM)).map(TraceLine::sequence)
                .findFirst().orElseThrow();
    }

    @Test
    void evaluate_whenLandBoughtAfter2001IsSold_noticesTheStampDutyValueAndImprovement() {
        // Given / When — a plot bought 2015-06-01 and sold in TY 2026-27
        NodeResult result = run(2026, land(LocalDate.of(2015, 6, 1), SOLD_2026));

        // Then — the stamp-duty value can raise the tax, improvement can only lower it; both on the term line
        assertThat(severities(result)).containsExactlyInAnyOrderEntriesOf(Map.of(STAMP_DUTY, Severity.ACTION_REQUIRED,
                IMPROVEMENT, Severity.ATTENTION));
        assertThat(result.annotations()).extracting(TraceAnnotation::sequence).containsOnly(termLine(result));
    }

    @Test
    void evaluate_whenLandBoughtBefore2001IsSold_noticesTheFairMarketValueOptionToo() {
        // Given / When — bought the day before 1-4-2001
        NodeResult result = run(2026, land(LocalDate.of(2001, 3, 31), SOLD_2026));

        // Then
        assertThat(severities(result)).containsExactlyInAnyOrderEntriesOf(Map.of(STAMP_DUTY, Severity.ACTION_REQUIRED,
                IMPROVEMENT, Severity.ATTENTION, VALUE_2001, Severity.ATTENTION));
    }

    @Test
    void evaluate_whenLandBoughtOn1April2001IsSold_leavesTheFairMarketValueOptionOut() {
        // Given / When — bought on the day itself: "before" does not reach it
        NodeResult result = run(2026, land(LocalDate.of(2001, 4, 1), SOLD_2026));

        // Then
        assertThat(severities(result)).containsOnlyKeys(STAMP_DUTY, IMPROVEMENT);
    }

    @Test
    void evaluate_whenEquityIsSold_noticesNothing() {
        // Given — listed equity sold on NSE with STT paid both sides
        SideVenue nse = new SideVenue(Optional.of("NSE"), Optional.empty());
        CapitalGainsInput.Bucket shares = new CapitalGainsInput.Bucket(Money.of("2000000"), Money.of("1000000"),
                List.of(), new BucketFacts("EQUITY", "PAID", "PAID", "PURCHASE", Optional.empty(),
                        LocalDate.of(1999, 6, 1), SOLD_2026, nse, nse), Optional.empty());

        // When
        NodeResult result = run(2026, shares);

        // Then
        assertThat(result.annotations()).isEmpty();
    }

    @Test
    void evaluate_whenLandIsSoldInTaxYear2025_namesBothActsSections() {
        // Given / When — the same plot sold in TY 2025-26, which the 1961 Act governs
        NodeResult result = run(2025, land(LocalDate.of(2015, 6, 1), LocalDate.of(2025, 9, 1)));

        // Then — the notice names the provision in both Acts, as every trace line does
        assertThat(result.annotations()).flatExtracting(TraceAnnotation::notices)
                .filteredOn(notice -> notice.relatedRules().contains(STAMP_DUTY))
                .singleElement()
                .satisfies(notice -> assertThat(notice.message()).contains("1961 s.50C(1)").contains("2025 s.78(1)"));
    }
}
