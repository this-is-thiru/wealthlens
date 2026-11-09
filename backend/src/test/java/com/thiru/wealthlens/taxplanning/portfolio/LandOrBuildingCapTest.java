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
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.BucketFacts;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.SideVenue;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.Individual;
import com.thiru.wealthlens.taxengine.node.CapitalGainsNode;
import com.thiru.wealthlens.taxengine.node.TaxByBasisNode;
import com.thiru.wealthlens.taxengine.pipeline.CapitalGainsHead;
import com.thiru.wealthlens.taxengine.pipeline.HeadsAfterSetOff;
import com.thiru.wealthlens.taxengine.pipeline.IndexedGain;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.SpecialRateId;
import com.thiru.wealthlens.taxengine.pipeline.TaxByBasis;
import com.thiru.wealthlens.taxengine.rules.condition.ResidentialStatus;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The cap on the tax on land or building acquired before 23-7-2024 (1961 s.112(1)(a) second proviso; 2025 s.197(3);
 * W-088g, D-426): the lesser of twelve and one-half per cent of the gain and twenty per cent of the gain computed with
 * the cost lifted by the Cost Inflation Index — for a resident individual or HUF only, and with the divisor never taken
 * earlier than 1-4-2001.
 *
 * <p>Every figure is worked by hand in {@code plans/W-088g.md} — the indexed cost as {@code cost × index(sale year) ÷
 * index(later of the acquisition year and 2001-02)}, rounded once to paise — and never taken from a run.
 */
@ExtendWith(MockitoExtension.class)
class LandOrBuildingCapTest {

    private static final String EMAIL = "user@example.com";

    private static final CapitalGainsPart LONG_TERM_OTHER = new CapitalGainsPart("LONG_TERM_OTHER");

    private static final RegimeCode FIRST_SCHEDULE_RATES = new RegimeCode("FIRST_SCHEDULE_RATES");

    private static final SpecialRateId LONG_OTHER = new SpecialRateId("RATE_CG_LONG_OTHER");

    private static final LocalDate SOLD_2026 = LocalDate.of(2026, 9, 1);

    private static final String AT_TWELVE = "twelve and one-half per cent of the gain";

    private static final String AT_TWENTY = "twenty per cent of the gain computed with indexed cost";

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

    /** The head one {@code trade_outcome} row makes, through the summary, the adapter and {@code CapitalGainsNode}. */
    private CapitalGainsHead headThroughPortfolio(int year, TradeOutcomeEntity row) {
        when(tradeOutcomeRepository.findByEmailAndSellDateBetween(eq(EMAIL), any(), any())).thenReturn(List.of(row));
        CapitalGainsInput input = new CapitalGainsAdapter(new CapitalGainsSummaryService(tradeOutcomeRepository))
                .inputFor(EMAIL, TaxYearId.of(year));
        return (CapitalGainsHead) CapitalGainsHarness.run(year, input).value();
    }

    /** A land bucket bought and sold as stated, for {@code cost}, with nothing on record for either side's STT. */
    private static CapitalGainsInput.Bucket bucket(LocalDate bought, LocalDate sold, String cost,
            String consideration) {
        BucketFacts facts = new BucketFacts("LAND_OR_BUILDING", "UNKNOWN", "UNKNOWN", "PURCHASE", Optional.empty(),
                bought, sold, SideVenue.UNRECORDED, SideVenue.UNRECORDED);
        return new CapitalGainsInput.Bucket(Money.of(consideration), Money.of(cost), List.of(), facts,
                Optional.empty());
    }

    /**
     * Equity sold on NSE with STT paid on the buy and not on the sale — the other kind {@code LONG_TERM_OTHER} holds
     * ({@code CG-PART-LONG-OTHER}).
     */
    private static CapitalGainsInput.Bucket sharesWithoutStt(LocalDate bought, LocalDate sold, String cost,
            String consideration) {
        SideVenue nse = new SideVenue(Optional.of("NSE"), Optional.empty());
        BucketFacts facts = new BucketFacts("EQUITY", "PAID", "NOT_PAID", "PURCHASE", Optional.empty(), bought, sold,
                nse, nse);
        return new CapitalGainsInput.Bucket(Money.of(consideration), Money.of(cost), List.of(), facts,
                Optional.empty());
    }

    /** The 2005 plot of the TY 2026-27 cases: 10,00,000 → 40,00,000, indexed cost 32,82,051.28 (117 → 384). */
    private static CapitalGainsInput.Bucket plot() {
        return bucket(LocalDate.of(2005, 6, 1), SOLD_2026, "1000000", "4000000");
    }

    /** TY {@code year}'s head for the taxpayer {@code status} describes. */
    private static CapitalGainsHead head(int year, ResidentialStatus status, CapitalGainsInput.Bucket... buckets) {
        TaxYearId taxYear = TaxYearId.of(year);
        return (CapitalGainsHead) NodeHarness.with(pack(taxYear)).regime(FIRST_SCHEDULE_RATES)
                .inputs(inputs(year, status, CapitalGainsHarness.inputOf(buckets))).run(new CapitalGainsNode()).value();
    }

    /**
     * TY {@code year}'s tax on {@code head} at slab rates on {@code 10,00,000} of other income — above the nil band
     * ceiling of both taxes' ladders, so no s.197(2) shortfall reaches the gain — with the head's indexed measure
     * handed to {@code TaxByBasisNode} beside its parts.
     */
    private static NodeResult tax(int year, ResidentialStatus status, CapitalGainsHead head) {
        return tax(year, status, head, Money.of("1000000"));
    }

    /** As {@link #tax(int, ResidentialStatus, CapitalGainsHead)}, on {@code otherIncome} of other income. */
    private static NodeResult tax(int year, ResidentialStatus status, CapitalGainsHead head, Money otherIncome) {
        TaxYearId taxYear = TaxYearId.of(year);
        Money totalIncome = otherIncome.plus(head.total());
        return NodeHarness.with(pack(taxYear)).regime(FIRST_SCHEDULE_RATES)
                .inputs(inputs(year, status, CapitalGainsInput.none(Instant.parse("2026-04-15T10:00:00Z"), "cap-run")))
                .given(NodeIds.ROUND_TOTALINCOME, totalIncome)
                .given(NodeIds.SETOFF, new HeadsAfterSetOff(totalIncome, head.parts(), head.indexedGains()))
                .run(new TaxByBasisNode());
    }

    private static TaxByBasis valueOf(NodeResult result) {
        return (TaxByBasis) result.value();
    }

    private static EngineInputs inputs(int year, ResidentialStatus status, CapitalGainsInput gains) {
        TaxYearId taxYear = TaxYearId.of(year);
        EngineInputs plain = NodeHarness.plainTaxpayer();
        Individual person = new Individual(taxYear.endsOn().minusYears(40).minusMonths(6), status, Optional.empty(),
                Optional.empty());
        return new EngineInputs(taxYear, FIRST_SCHEDULE_RATES,
                TemporalContext.forTaxYear(taxYear, taxYear.startsOn().plusMonths(5)), plain.entered(), person,
                plain.family(), plain.ledgers(), plain.elections(), gains, plain.filing());
    }

    private static RulePack pack(TaxYearId taxYear) {
        return new ClasspathRulePackProvider("rulepacks", Set.of(taxYear),
                new JsonPackAssembler(LockedInstruments.of(taxYear))).load(taxYear);
    }

    @Test
    void taxOnLandGain_whenIndexationHelpsLittle_chargesTheTwelveAndAHalfPerCentLimb() {
        // Given — bought 1-6-2015 for 40,00,000, sold 1-9-2025 for 1,00,00,000: the CII for 2015-16 is 254 and for
        // 2025-26 is 376, so the indexed cost is 40,00,000 × 376 ÷ 254 = 59,21,259.84 and the indexed gain 40,78,740.16
        CapitalGainsHead head = headThroughPortfolio(2025,
                land(LocalDate.of(2015, 6, 1), LocalDate.of(2025, 9, 1), 4000000, 10000000));

        // When
        NodeResult result = tax(2025, ResidentialStatus.RESIDENT, head);

        // Then — 12.5 % of 60,00,000 is 7,50,000 against 20 % of 40,78,740.16 = 8,15,748.03, so the first limb is less
        assertThat(head.indexedGains(LONG_TERM_OTHER)).extracting(IndexedGain::indexedGain).containsExactly(Money.of("4078740.16"));
        assertThat(valueOf(result).at(LONG_OTHER)).isEqualTo(Money.of("750000"));
        assertThat(result.trace())
                .filteredOn(line -> line.rule().equals(RuleIds.RATE_CG_LONG_OTHER))
                .singleElement()
                .satisfies(line -> assertThat(line.limitedBy().orElseThrow())
                        .isEqualTo("LONG_TERM_OTHER; " + AT_TWELVE));
    }

    @Test
    void taxOnLandGain_whenTheCostRoseMoreThanTheValue_chargesTheTwentyPerCentLimb() {
        // Given — bought 1-6-2005 for 10,00,000, sold 1-9-2025 for 40,00,000: 117 → 376, so the indexed cost is
        // 10,00,000 × 376 ÷ 117 = 32,13,675.21 and the indexed gain 7,86,324.79
        CapitalGainsHead head = headThroughPortfolio(2025,
                land(LocalDate.of(2005, 6, 1), LocalDate.of(2025, 9, 1), 1000000, 4000000));

        // When
        NodeResult result = tax(2025, ResidentialStatus.RESIDENT, head);

        // Then — 12.5 % of 30,00,000 is 3,75,000 against 20 % of 7,86,324.79 = 1,57,264.96; the cap binds
        assertThat(head.indexedGains(LONG_TERM_OTHER)).extracting(IndexedGain::indexedGain).containsExactly(Money.of("786324.79"));
        assertThat(valueOf(result).at(LONG_OTHER)).isEqualTo(Money.of("157264.96"));
        assertThat(result.trace())
                .filteredOn(line -> line.rule().equals(RuleIds.RATE_CG_LONG_OTHER))
                .singleElement()
                .satisfies(line -> assertThat(line.limitedBy().orElseThrow())
                        .isEqualTo("LONG_TERM_OTHER; " + AT_TWENTY));
    }

    @Test
    void taxOnLandGain_whenTheCostIsIndexed_tracesTheCostTheTwoYearsAndTheIndexedCost() {
        // When
        NodeResult result = NodeHarness.with(pack(TaxYearId.of(2025))).regime(FIRST_SCHEDULE_RATES)
                .inputs(inputs(2025, ResidentialStatus.RESIDENT, CapitalGainsHarness.inputOf(
                        bucket(LocalDate.of(2005, 6, 1), LocalDate.of(2025, 9, 1), "1000000", "4000000"))))
                .run(new CapitalGainsNode());

        // Then — the measure line is the qualifying row's, and it says how the index was applied
        assertThat(result.trace()).filteredOn(line -> line.rule().equals(RuleIds.CG_INDEXED_COST_LAND_BUILDING))
                .singleElement()
                .satisfies(line -> assertThat(line.limitedBy().orElseThrow())
                        .isEqualTo("the cost 1000000.00 indexed by the index for 2025-26 (376) over that for 2005-06"
                                + " (117): 3213675.21"));
    }

    @Test
    void taxOnLandGain_whenTheSaleFallsInTheSecondPack_usesThatYearsIndex() {
        // Given — the same 2005-06 purchase sold 1-9-2026, in TY 2026-27, whose index for the sale year is 384:
        // 10,00,000 × 384 ÷ 117 = 32,82,051.28, so the indexed gain is 7,17,948.72
        CapitalGainsHead head = headThroughPortfolio(2026,
                land(LocalDate.of(2005, 6, 1), LocalDate.of(2026, 9, 1), 1000000, 4000000));

        // When
        NodeResult result = tax(2026, ResidentialStatus.RESIDENT, head);

        // Then — 20 % of 7,17,948.72 = 1,43,589.74 against 12.5 % of 30,00,000 = 3,75,000
        assertThat(head.indexedGains(LONG_TERM_OTHER)).extracting(IndexedGain::indexedGain).containsExactly(Money.of("717948.72"));
        assertThat(valueOf(result).at(LONG_OTHER)).isEqualTo(Money.of("143589.74"));
    }

    @Test
    void taxOnLandGain_whenAcquiredTheDayBeforeTheDate_indexesTheCost() {
        // Given — bought 22-7-2024 for 10,00,000, sold 1-9-2026 for 11,00,000: 363 → 384, so the indexed cost is
        // 10,00,000 × 384 ÷ 363 = 10,57,851.24 and the indexed gain 42,148.76
        CapitalGainsHead head = head(2026, ResidentialStatus.RESIDENT,
                bucket(LocalDate.of(2024, 7, 22), LocalDate.of(2026, 9, 1), "1000000", "1100000"));

        // When
        NodeResult result = tax(2026, ResidentialStatus.RESIDENT, head);

        // Then — 20 % of 42,148.76 = 8,429.75 against 12.5 % of 1,00,000 = 12,500
        assertThat(head.indexedGains(LONG_TERM_OTHER)).extracting(IndexedGain::indexedGain).containsExactly(Money.of("42148.76"));
        assertThat(valueOf(result).at(LONG_OTHER)).isEqualTo(Money.of("8429.75"));
    }

    @Test
    void taxOnLandGain_whenAcquiredOnTheDate_hasNoIndexedMeasureAtAll() {
        // Given — the same purchase one day later, 23-7-2024: "before" does not reach the day itself
        CapitalGainsHead head = head(2026, ResidentialStatus.RESIDENT,
                bucket(LocalDate.of(2024, 7, 23), LocalDate.of(2026, 9, 1), "1000000", "1100000"));

        // When
        NodeResult result = tax(2026, ResidentialStatus.RESIDENT, head);

        // Then — no row of RS-CG-INDEXED-COST meets the bucket, so the part lists no indexed gain and its whole gain is
        // the rest, both bases alike: 20 % of 1,00,000 = 20,000 cannot be less than 12.5 % of it = 12,500
        assertThat(head.indexedGains(LONG_TERM_OTHER)).isEmpty();
        assertThat(valueOf(result).at(LONG_OTHER)).isEqualTo(Money.of("12500"));
    }

    @Test
    void taxOnLandGain_whenTheTaxpayerIsNonResident_hasNoIndexedMeasureAtAll() {
        // Given — the 2005-06 purchase of the binding case, for a non-resident: s.197(3) is resident-only
        CapitalGainsHead head = head(2025, ResidentialStatus.NON_RESIDENT,
                bucket(LocalDate.of(2005, 6, 1), LocalDate.of(2025, 9, 1), "1000000", "4000000"));

        // When
        NodeResult result = tax(2025, ResidentialStatus.NON_RESIDENT, head);

        // Then — the bucket lists no indexed gain, so the part is charged as the rest: 20 % of the gain itself against
        // 12.5 %, the lesser
        assertThat(head.indexedGains(LONG_TERM_OTHER)).isEmpty();
        assertThat(valueOf(result).at(LONG_OTHER)).isEqualTo(Money.of("375000"));
    }

    @Test
    void taxOnLandGain_whenTheLandSharesThePartWithAnotherGain_capsTheLandGainAlone() {
        // Given — the plot (gain 30,00,000, indexed 7,17,948.72) and shares sold without STT, 10,00,000 → 20,00,000
        CapitalGainsHead head = head(2026, ResidentialStatus.RESIDENT, plot(),
                sharesWithoutStt(LocalDate.of(2022, 6, 1), SOLD_2026, "1000000", "2000000"));

        // When
        NodeResult result = tax(2026, ResidentialStatus.RESIDENT, head);

        // Then — the plot: min(3,75,000, 1,43,589.74); the shares at 12.5 % of 10,00,000 = 1,25,000
        assertThat(valueOf(result).at(LONG_OTHER)).isEqualTo(Money.of("268589.74"));
    }

    @Test
    void taxOnLandGain_whenTwoLandSalesSitInOneYear_capsEachOnItsOwn() {
        // Given — the plot, and a flat bought 2015-06-01 for 40,00,000 and sold for 1,00,00,000: indexed cost
        // 40,00,000 × 384 ÷ 254 = 60,47,244.09, indexed gain 39,52,755.91
        CapitalGainsHead head = head(2026, ResidentialStatus.RESIDENT, plot(),
                bucket(LocalDate.of(2015, 6, 1), SOLD_2026, "4000000", "10000000"));

        // When
        NodeResult result = tax(2026, ResidentialStatus.RESIDENT, head);

        // Then — 1,43,589.74 for the plot plus min(7,50,000, 7,90,551.18) for the flat
        assertThat(valueOf(result).at(LONG_OTHER)).isEqualTo(Money.of("893589.74"));
    }

    @Test
    void taxOnLandGain_whenTheNilBandShiftsOntoThePart_takesItFromEachGainInProportion() {
        // Given — the plot and the shares, and no other income: the 2,50,000 nil band shifts onto the part
        CapitalGainsHead head = head(2026, ResidentialStatus.RESIDENT, plot(),
                sharesWithoutStt(LocalDate.of(2022, 6, 1), SOLD_2026, "1000000", "2000000"));

        // When
        NodeResult result = tax(2026, ResidentialStatus.RESIDENT, head, Money.ZERO);

        // Then — the plot bears 2,50,000 × 30 ÷ 40 = 1,87,500: 28,12,500 and indexed 5,30,448.72, so 1,06,089.74;
        // the shares 9,37,500 at 12.5 % = 1,17,187.50
        assertThat(valueOf(result).at(LONG_OTHER)).isEqualTo(Money.of("223277.24"));
    }

    @Test
    void taxOnLandGain_whenALosingLotNetsIntoThePart_takesItFromEachGainInProportion() {
        // Given — the plot, the shares, and a second lot sold without STT losing 4,00,000 (10,00,000 → 6,00,000)
        CapitalGainsHead head = head(2026, ResidentialStatus.RESIDENT, plot(),
                sharesWithoutStt(LocalDate.of(2022, 6, 1), SOLD_2026, "1000000", "2000000"),
                sharesWithoutStt(LocalDate.of(2023, 6, 1), SOLD_2026, "1000000", "600000"));

        // When
        NodeResult result = tax(2026, ResidentialStatus.RESIDENT, head);

        // Then — the plot bears 4,00,000 × 30 ÷ 40 = 3,00,000: 27,00,000 and indexed 4,17,948.72, so 83,589.74; the
        // shares bear 1,00,000: 9,00,000 at 12.5 % = 1,12,500
        assertThat(head.part(LONG_TERM_OTHER)).isEqualTo(Money.of("3600000"));
        assertThat(valueOf(result).at(LONG_OTHER)).isEqualTo(Money.of("196089.74"));
    }
}
