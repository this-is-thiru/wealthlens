package com.thiru.wealthlens.taxplanning.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.thiru.wealthlens.portfolio.dto.enums.AcquisitionMode;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.portfolio.repository.TradeOutcomeRepository;
import com.thiru.wealthlens.portfolio.service.CapitalGainsSummaryService;
import com.thiru.wealthlens.taxengine.input.Assessee.ResidentialStatus;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.BucketFacts;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.SideVenue;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.Individual;
import com.thiru.wealthlens.taxengine.node.TaxByBasisNode;
import com.thiru.wealthlens.taxengine.pipeline.CapitalGainsHead;
import com.thiru.wealthlens.taxengine.pipeline.HeadsAfterSetOff;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.SpecialRateId;
import com.thiru.wealthlens.taxengine.pipeline.TaxByBasis;
import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsPart;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.test.CapitalGainsHarness;
import com.thiru.wealthlens.taxengine.test.LockedInstruments;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * A land or building sale, from {@code portfolio}'s row to the tax charged on it (W-088g2, D-425): twenty-four
 * months decides the term (1961 s.2(42A) after the Finance (No. 2) Act, 2024 s.3(b); 2025 s.2(101)(a)), the part is
 * the one that provision's gain goes to, and the rate is s.112(1)(a)(ii)(B)'s twelve and one-half per cent.
 *
 * <p>Every figure is worked by hand in {@code plans/W-088g2.md}, not taken from a run.
 */
@ExtendWith(MockitoExtension.class)
class LandOrBuildingCapitalGainsTest {

    private static final String EMAIL = "user@example.com";

    private static final CapitalGainsPart LONG_TERM_OTHER = new CapitalGainsPart("LONG_TERM_OTHER");

    private static final CapitalGainsPart SHORT_TERM_SLAB = new CapitalGainsPart("SHORT_TERM_SLAB");

    private static final RegimeCode FIRST_SCHEDULE_RATES = new RegimeCode("FIRST_SCHEDULE_RATES");

    private static final SpecialRateId LONG_OTHER = new SpecialRateId("RATE_CG_LONG_OTHER");

    @Mock
    private TradeOutcomeRepository tradeOutcomeRepository;

    /** One plot bought for {@code costRupees} and sold for {@code considerationRupees}, each quantity one. */
    private static TradeOutcomeEntity land(LocalDate bought, LocalDate sold, double costRupees,
            double considerationRupees) {
        TradeOutcomeEntity row = new TradeOutcomeEntity();
        row.setId("PLOT-1");
        row.setStockCode("PLOT-1");
        row.setAssetType(AssetType.LAND_OR_BUILDING);
        row.setSellDate(sold);
        row.setBuyDate(bought);
        row.setSellPrice(considerationRupees);
        row.setSellQuantity(1.0);
        row.setCorporateActionAdjustedBuyPrice(costRupees);
        row.setBuyQuantity(1.0);
        row.setAcquisitionMode(AcquisitionMode.PURCHASE);
        return row;
    }

    /** The run one {@code trade_outcome} row makes, through the summary, the adapter and {@code CapitalGainsNode}. */
    private NodeResult runThroughPortfolio(TradeOutcomeEntity row) {
        when(tradeOutcomeRepository.findByEmailAndSellDateBetween(eq(EMAIL), any(), any())).thenReturn(List.of(row));
        CapitalGainsInput input = new CapitalGainsAdapter(new CapitalGainsSummaryService(tradeOutcomeRepository))
                .inputFor(EMAIL, TaxYearId.of(2025));
        return CapitalGainsHarness.run(2025, input);
    }

    /** A land bucket sold as stated, with nothing on record for either side's STT. */
    private static CapitalGainsInput.Bucket bucket(LocalDate bought, LocalDate sold) {
        BucketFacts facts = new BucketFacts("LAND_OR_BUILDING", "UNKNOWN", "UNKNOWN", "PURCHASE", Optional.empty(),
                bought, sold, SideVenue.UNRECORDED, SideVenue.UNRECORDED);
        return new CapitalGainsInput.Bucket(Money.of("1000"), Money.ZERO, List.of(), facts, Optional.empty());
    }

    private static CapitalGainsHead head(int year, CapitalGainsInput.Bucket... buckets) {
        return (CapitalGainsHead) CapitalGainsHarness.run(year, CapitalGainsHarness.inputOf(buckets)).value();
    }

    /** TY {@code year}'s tax on {@code head}, with nothing else of the head's parts in the return. */
    private static TaxByBasis taxOnLandGain(int year, CapitalGainsHead head) {
        TaxYearId taxYear = TaxYearId.of(year);
        RulePack pack = new ClasspathRulePackProvider("rulepacks", Set.of(taxYear),
                new JsonPackAssembler(LockedInstruments.of(taxYear))).load(taxYear);
        EngineInputs plain = NodeHarness.plainTaxpayer();
        Individual person = new Individual(taxYear.endsOn().minusYears(40).minusMonths(6),
                ResidentialStatus.RESIDENT, Optional.empty(), Optional.empty());
        EngineInputs inputs = new EngineInputs(taxYear, FIRST_SCHEDULE_RATES,
                TemporalContext.forTaxYear(taxYear, taxYear.startsOn().plusMonths(5)), plain.entered(), person,
                plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(), plain.filing());
        // 10,00,000 at slab rates uses the basic exemption up, so no s.112(1) proviso shortfall reaches the gain
        Money totalIncome = Money.of("1000000").plus(head.total());
        NodeResult result = NodeHarness.with(pack).regime(FIRST_SCHEDULE_RATES).inputs(inputs)
                .given(NodeIds.ROUND_TOTALINCOME, totalIncome)
                .given(NodeIds.SETOFF, new HeadsAfterSetOff(totalIncome, head.parts()))
                .run(new TaxByBasisNode());
        return (TaxByBasis) result.value();
    }

    @Test
    void inputFor_whenALandRowKeepsNoChargeBreakdown_isInTheLongTermOtherPart() {
        // Given — bought 1-6-2015 for 40,00,000, sold 1-9-2025 for 1,00,00,000; no breakdown, so neither side's
        // STT is known, which the new rows do not ask (D-292)
        // When
        CapitalGainsHead head = (CapitalGainsHead) runThroughPortfolio(
                land(LocalDate.of(2015, 6, 1), LocalDate.of(2025, 9, 1), 4000000, 10000000)).value();

        // Then — 1,00,00,000 − 40,00,000 = 60,00,000, charged under s.197(1) / s.112(1) rather than s.112A
        assertThat(head.part(LONG_TERM_OTHER)).isEqualTo(Money.of("6000000"));
        assertThat(head.total()).isEqualTo(Money.of("6000000"));
    }

    @Test
    void inputFor_whenTheLandIsLongTerm_tracesTheTwentyFourMonthRow() {
        // When
        NodeResult result = runThroughPortfolio(
                land(LocalDate.of(2015, 6, 1), LocalDate.of(2025, 9, 1), 4000000, 10000000));

        // Then — the term is the new row's, and the row's own period is what decided it
        assertThat(result.trace()).filteredOn(line -> line.rule().equals(RuleIds.CG_TERM_LAND_BUILDING))
                .singleElement()
                .satisfies(line -> assertThat(line.limitedBy().orElseThrow())
                        .isEqualTo("Held 2015-06-01 → 2025-09-01: more than 24 months, LONG_TERM (calendar months,"
                                + " D-303)"));
    }

    @Test
    void taxOnLandGain_whenTheGainIsSixtyLakh_chargesSevenLakhFiftyThousandAtTwelveAndAHalf() {
        // Given — the 60,00,000 long-term gain above, and 10,00,000 at slab rates
        CapitalGainsHead head = (CapitalGainsHead) runThroughPortfolio(
                land(LocalDate.of(2015, 6, 1), LocalDate.of(2025, 9, 1), 4000000, 10000000)).value();

        // When
        TaxByBasis tax = taxOnLandGain(2025, head);

        // Then — 60,00,000 × 12.5 % = 7,50,000 (s.112(1)(a)(ii)(B), TY 2025-26)
        assertThat(tax.at(LONG_OTHER)).isEqualTo(Money.of("750000"));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void head_whenLandIsHeldSevenMonths_isInTheShortTermSlabPart(int year) {
        // When — bought 10-1-2025, sold 1-9-2025: seven calendar months, so short-term
        CapitalGainsHead head = head(year, bucket(LocalDate.of(2025, 1, 10), LocalDate.of(2025, 9, 1)));

        // Then — no special-rate provision charges it; it goes with the rest of total income
        assertThat(head.part(SHORT_TERM_SLAB)).isEqualTo(Money.of("1000"));
        assertThat(head.part(LONG_TERM_OTHER)).isEqualTo(Money.ZERO);
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void taxOnLandGain_whenTheLandWasShortTerm_chargesNothingAtTheLongTermRate(int year) {
        // Given — the seven-month lot
        CapitalGainsHead head = head(year, bucket(LocalDate.of(2025, 1, 10), LocalDate.of(2025, 9, 1)));

        // When
        TaxByBasis tax = taxOnLandGain(year, head);

        // Then — the whole 1,000 is at slab rates
        assertThat(tax.at(LONG_OTHER)).isEqualTo(Money.ZERO);
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void head_whenLandIsHeldExactlyTwentyFourMonths_isShortTermButAMonthLongerIsLongTerm(int year) {
        // When — 1-9-2023 → 1-9-2025 is exactly twenty-four calendar months, 1-8-2023 → 1-9-2025 is twenty-five
        CapitalGainsHead held = head(year, bucket(LocalDate.of(2023, 9, 1), LocalDate.of(2025, 9, 1)),
                bucket(LocalDate.of(2023, 8, 1), LocalDate.of(2025, 9, 1)));

        // Then — "not more than twenty-four months" is short-term (D-303); a twelve-month row would have called both
        // long-term, and a thirty-six-month one, neither
        assertThat(held.part(SHORT_TERM_SLAB)).isEqualTo(Money.of("1000"));
        assertThat(held.part(LONG_TERM_OTHER)).isEqualTo(Money.of("1000"));
    }
}
