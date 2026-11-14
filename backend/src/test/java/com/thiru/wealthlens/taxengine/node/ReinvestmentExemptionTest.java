package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.CapitalGainsInput;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.BucketFacts;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.SideVenue;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.HouseReinvestment;
import com.thiru.wealthlens.taxengine.pipeline.CapitalGainsHead;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.NodeValue;
import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsPart;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.ReasonCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.reason.Reason;
import com.thiru.wealthlens.taxengine.rules.reason.ReasonCodes;
import com.thiru.wealthlens.taxengine.rules.reason.ReasonFact;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.test.LockedInstruments;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The house-reinvestment exemption on the node's own cases (2025 s.86; 1961 s.54F; W-088i): the cap on the new house's
 * cost, the bar on other houses, the construction limb, and a window missed on each side. Every figure is worked by
 * hand in {@code plans/W-088i.md} from the row the case's own facts meet, never taken from a run.
 */
class ReinvestmentExemptionTest {

    private static final CapitalGainsPart LONG_TERM_LISTED_EQUITY = new CapitalGainsPart("LONG_TERM_LISTED_EQUITY");

    private static final CapitalGainsPart SHORT_TERM_LISTED_EQUITY = new CapitalGainsPart("SHORT_TERM_LISTED_EQUITY");

    private static final SideVenue SOLD_ON_NSE = new SideVenue(Optional.of("NSE"), Optional.empty());

    private static final LocalDate SOLD = LocalDate.of(2026, 9, 1);

    private static RulePack pack(int year) {
        TaxYearId taxYear = TaxYearId.of(year);
        return new ClasspathRulePackProvider("rulepacks", Set.of(taxYear),
                new JsonPackAssembler(LockedInstruments.of(taxYear))).load(taxYear);
    }

    /**
     * An equity lot bought on {@code acquiredOn} for {@code cost} and sold on NSE for {@code consideration} on
     * {@code soldOn}, STT paid on both sides and no charge recorded — so the net consideration is the consideration.
     */
    private static CapitalGainsInput.Bucket bucket(String consideration, String cost, LocalDate acquiredOn,
            LocalDate soldOn) {
        BucketFacts facts = new BucketFacts("EQUITY", "PAID", "PAID", "PURCHASE", Optional.empty(), acquiredOn,
                soldOn, SideVenue.UNRECORDED, SOLD_ON_NSE);
        return new CapitalGainsInput.Bucket(Money.of(consideration), Money.of(cost), List.of(), facts,
                Optional.empty());
    }

    /** A lot of the golden's figures, bought 2024-05-01 and sold on {@code soldOn}. */
    private static CapitalGainsInput.Bucket goldenBucket(LocalDate soldOn) {
        return bucket("12000000", "4000000", LocalDate.of(2024, 5, 1), soldOn);
    }

    /** A house bought for {@code cost} on {@code newAssetOn}, with no other house in the way. */
    private static HouseReinvestment house(LocalDate transfer, LocalDate newAssetOn, String cost) {
        return new HouseReinvestment(transfer, newAssetOn, false, Money.of(cost), 0, 0);
    }

    /** The head on {@code buckets}, the taxpayer having entered {@code reinvestments}. */
    private static NodeResult result(int year, List<HouseReinvestment> reinvestments,
            List<CapitalGainsInput.Bucket> buckets) {
        TaxYearId taxYear = TaxYearId.of(year);
        RulePack pack = pack(year);
        EngineInputs plain = NodeHarness.plainTaxpayer();
        EnteredFacts entered = EnteredFacts.none().withHouseReinvestments(reinvestments);
        EngineInputs inputs = new EngineInputs(taxYear, plain.regime(),
                TemporalContext.forTaxYear(taxYear, taxYear.startsOn().plusMonths(5)), entered, plain.assessee(),
                plain.family(), plain.ledgers(), plain.elections(),
                new CapitalGainsInput(buckets, Instant.parse("2026-04-15T10:00:00Z"), "run-1"),
                plain.filing());
        return NodeHarness.with(pack).regime(plain.regime()).inputs(inputs).run(new CapitalGainsNode());
    }

    private static NodeResult result(int year, List<HouseReinvestment> reinvestments, CapitalGainsInput.Bucket bucket) {
        return result(year, reinvestments, List.of(bucket));
    }

    private static NodeResult result(int year, HouseReinvestment reinvestment, CapitalGainsInput.Bucket bucket) {
        return result(year, List.of(reinvestment), bucket);
    }

    /** The head on several buckets, one house claimed for {@link #SOLD}. */
    private static NodeResult result(HouseReinvestment reinvestment, CapitalGainsInput.Bucket... buckets) {
        return result(2026, List.of(reinvestment), List.of(buckets));
    }

    /** A lot of {@code consideration} on {@code cost}, bought on {@code acquiredOn} and sold on {@link #SOLD}. */
    private static CapitalGainsInput.Bucket lot(String consideration, String cost, LocalDate acquiredOn) {
        return bucket(consideration, cost, acquiredOn, SOLD);
    }

    private static CapitalGainsHead head(NodeResult result) {
        return (CapitalGainsHead) result.value();
    }

    /** The exemption lines' gains in and out, in the order written. */
    private static List<List<NodeValue>> exemptionFigures(NodeResult result) {
        return result.trace().stream()
                .filter(line -> line.rule().equals(RuleIds.CG_EXEMPT_RESIDENTIAL_HOUSE))
                .filter(line -> line.kind() == StepKind.EXEMPTION)
                .map(line -> List.of(line.in(), line.out()))
                .toList();
    }

    private static List<NodeValue> inOut(String in, String out) {
        return List.<NodeValue>of(MoneyValue.of(Money.of(in)), MoneyValue.of(Money.of(out)));
    }

    /** The one exemption line, its figures the gain before and after; the reason it exempted nothing is its own. */
    private static TraceLine exemptionLine(NodeResult result, Money before, Money after) {
        return result.trace().stream()
                .filter(line -> line.rule().equals(RuleIds.CG_EXEMPT_RESIDENTIAL_HOUSE))
                .filter(line -> line.kind() == StepKind.EXEMPTION)
                .findFirst()
                .map(line -> {
                    assertThat(line.in()).isEqualTo(MoneyValue.of(before));
                    assertThat(line.out()).isEqualTo(MoneyValue.of(after));
                    return line;
                })
                .orElseThrow(() -> new AssertionError("no exemption line in " + result.trace()));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenTheNewHouseIsBoughtInsideTheWindow_exemptsTheCostsShareOfTheGain(int year) {
        // Given — a gain of 80,00,000 on an equity lot, and 60,00,000 of it put into a house after the sale
        LocalDate sold = year == 2026 ? SOLD : LocalDate.of(2025, 9, 1);
        LocalDate bought = year == 2026 ? LocalDate.of(2027, 1, 15) : LocalDate.of(2026, 1, 15);
        LocalDate acquired = year == 2026 ? LocalDate.of(2024, 5, 1) : LocalDate.of(2023, 5, 1);

        // When
        NodeResult result = result(year, house(sold, bought, "6000000"),
                bucket("12000000", "4000000", acquired, sold));

        // Then — 80,00,000 × 60,00,000 / 1,20,00,000, and the rest charged in the listed-equity part
        assertThat(exemptionLine(result, Money.of("8000000"), Money.of("4000000")).reason())
                .contains(costAgainst(ReasonCodes.CG_REINVESTMENT_SHARE, "6000000", "12000000"));
        assertThat(((CapitalGainsHead) result.value()).part(LONG_TERM_LISTED_EQUITY)).isEqualTo(Money.of("4000000"));
    }

    @Test
    void evaluate_whenTheNewHouseCostsMoreThanTheNetConsideration_exemptsTheWholeGain() {
        // Given — a gain of 30,00,000 and a house of 45,00,000 against a net consideration of 40,00,000
        // When
        NodeResult result = result(2026, house(SOLD, LocalDate.of(2027, 1, 15), "4500000"),
                bucket("4000000", "1000000", LocalDate.of(2024, 5, 1), SOLD));

        // Then — s.86(1)(ii) / s.54F(1)(a): the whole gain is exempt and no part is charged
        assertThat(exemptionLine(result, Money.of("3000000"), Money.ZERO).reason())
                .contains(costAgainst(ReasonCodes.CG_REINVESTMENT_WHOLE, "4500000", "4000000"));
        assertThat(((CapitalGainsHead) result.value()).part(LONG_TERM_LISTED_EQUITY)).isEqualTo(Money.ZERO);
    }

    @Test
    void evaluate_whenTheNewHouseCostsMoreThanTheCap_exemptsTheCappedSharesOfTheGain() {
        // Given — a gain of 15,00,00,000, a net consideration of 20,00,00,000, and a house of 12,00,00,000 whose
        // cost the row caps at 10,00,00,000
        // When
        NodeResult result = result(2026, house(SOLD, LocalDate.of(2027, 1, 15), "120000000"),
                bucket("200000000", "50000000", LocalDate.of(2024, 5, 1), SOLD));

        // Then — 15,00,00,000 × 10,00,00,000 / 20,00,00,000 = 7,50,00,000 exempt, and it says the cost was capped
        TraceLine line = exemptionLine(result, Money.of("150000000"), Money.of("75000000"));
        assertThat(line.limitedBy()).hasValueSatisfying(reason -> assertThat(reason).contains("capped at"));
        assertThat(line.reason()).contains(costAgainst(ReasonCodes.CG_REINVESTMENT_SHARE, "100000000", "200000000"));
        assertThat(((CapitalGainsHead) result.value()).part(LONG_TERM_LISTED_EQUITY)).isEqualTo(Money.of("75000000"));
    }

    @Test
    void evaluate_whenTheTaxpayerOwnsTwoOtherHouses_chargesTheWholeGain() {
        // Given — the golden's figures, and two other residential houses on the transfer date
        HouseReinvestment barred = new HouseReinvestment(SOLD, LocalDate.of(2027, 1, 15), false,
                Money.of("6000000"), 2, 0);

        // When
        NodeResult result = result(2026, barred, goldenBucket(SOLD));

        // Then — the row's bar is not met, so the node writes no exemption line and charges 80,00,000
        assertThat(result.trace()).noneMatch(line -> line.rule().equals(RuleIds.CG_EXEMPT_RESIDENTIAL_HOUSE));
        assertThat(((CapitalGainsHead) result.value()).part(LONG_TERM_LISTED_EQUITY)).isEqualTo(Money.of("8000000"));
    }

    @Test
    void evaluate_whenAnotherHouseIsBoughtInsideTheWindow_chargesTheWholeGain() {
        // Given — the golden's figures, and a second house bought after the sale inside the two-year window
        HouseReinvestment barred = new HouseReinvestment(SOLD, LocalDate.of(2027, 1, 15), false,
                Money.of("6000000"), 1, 1);

        // When
        NodeResult result = result(2026, barred, goldenBucket(SOLD));

        // Then
        assertThat(result.trace()).noneMatch(line -> line.rule().equals(RuleIds.CG_EXEMPT_RESIDENTIAL_HOUSE));
        assertThat(((CapitalGainsHead) result.value()).part(LONG_TERM_LISTED_EQUITY)).isEqualTo(Money.of("8000000"));
    }

    @ParameterizedTest(name = "the house bought on {0} is outside the window")
    @ValueSource(strings = {"2025-08-31", "2028-09-02"})
    void evaluate_whenTheNewHouseIsBoughtOutsideTheWindow_exemptsNothing(String boughtOn) {
        // Given — the golden's figures; the window runs from 2025-09-01 (a year before the sale) to 2028-09-01 (two
        // years after it)
        // When
        NodeResult result = result(2026, house(SOLD, LocalDate.parse(boughtOn), "6000000"), goldenBucket(SOLD));

        // Then — the row is met, so the line is written, and it says why nothing is exempt
        TraceLine line = exemptionLine(result, Money.of("8000000"), Money.of("8000000"));
        assertThat(line.limitedBy()).hasValueSatisfying(reason -> assertThat(reason).contains("outside the window"));
        assertThat(line.reason()).contains(outsideWindow(boughtOn, "2025-09-01", "2028-09-01"));
    }

    @ParameterizedTest(name = "the house bought on {0} is on the window's edge")
    @ValueSource(strings = {"2025-09-01", "2028-09-01"})
    void evaluate_whenTheNewHouseIsBoughtOnTheWindowsEdge_exemptsTheCostsShare(String boughtOn) {
        // Given — the same figures, a year before or two years after the sale to the day
        // When
        NodeResult result = result(2026, house(SOLD, LocalDate.parse(boughtOn), "6000000"), goldenBucket(SOLD));

        // Then
        exemptionLine(result, Money.of("8000000"), Money.of("4000000"));
    }

    @Test
    void evaluate_whenTheHouseIsBuiltInsideThreeYears_exemptsTheCostsShare() {
        // Given — the golden's figures, a house built 2027-01-15
        HouseReinvestment built = new HouseReinvestment(SOLD, LocalDate.of(2027, 1, 15), true,
                Money.of("6000000"), 0, 0);

        // When
        NodeResult result = result(2026, built, goldenBucket(SOLD));

        // Then — built within three years of the sale, so the same share is exempt
        exemptionLine(result, Money.of("8000000"), Money.of("4000000"));
    }

    @Test
    void evaluate_whenTheHouseIsBuiltAfterThreeYears_exemptsNothing() {
        // Given — the golden's figures, a house built 2029-09-02, past the three years the row gives
        HouseReinvestment built = new HouseReinvestment(SOLD, LocalDate.of(2029, 9, 2), true,
                Money.of("6000000"), 0, 0);

        // When
        NodeResult result = result(2026, built, goldenBucket(SOLD));

        // Then
        TraceLine line = exemptionLine(result, Money.of("8000000"), Money.of("8000000"));
        assertThat(line.limitedBy()).hasValueSatisfying(reason -> assertThat(reason).contains("outside the window"));
        assertThat(line.reason()).contains(outsideWindow("2029-09-02", "2026-09-01", "2029-09-01"));
    }

    @Test
    void evaluate_whenTheSaleShowsNoGainTogether_exemptsNothingAndSaysSo() {
        // Given — W-327d2: a lot gaining 10,00,000 (30,00,000 on 20,00,000) and a lot losing 20,00,000 (20,00,000
        // on 40,00,000), sold together, and a house of 60,00,000
        HouseReinvestment house = house(SOLD, LocalDate.of(2027, 1, 15), "6000000");

        // When
        NodeResult result = result(house, lot("3000000", "2000000", LocalDate.of(2023, 5, 1)),
                lot("2000000", "4000000", LocalDate.of(2024, 5, 1)));

        // Then — together −10,00,000, so nothing is exempt; the gaining lot's line says why
        assertThat(exemptionFigures(result)).containsExactly(inOut("1000000", "1000000"));
        assertThat(exemptionLine(result, Money.of("1000000"), Money.of("1000000")).reason())
                .contains(new Reason(ReasonCodes.CG_REINVESTMENT_NO_GAIN, Map.of()));
    }

    private static Reason costAgainst(ReasonCode code, String cost, String consideration) {
        return new Reason(code, Map.of("cost", new ReasonFact.MoneyFact(Money.of(cost)),
                "consideration", new ReasonFact.MoneyFact(Money.of(consideration))));
    }

    private static Reason outsideWindow(String house, String from, String to) {
        return new Reason(ReasonCodes.CG_REINVESTMENT_OUTSIDE_WINDOW, Map.of(
                "house", new ReasonFact.DateFact(LocalDate.parse(house)),
                "from", new ReasonFact.DateFact(LocalDate.parse(from)), "to", new ReasonFact.DateFact(LocalDate.parse(to))));
    }

    @ParameterizedTest(name = "a house of {0} against {1} exempts {2} of a gain of 30,000")
    @CsvSource({"10000, 40000, 7500", "39999, 40000, 29999.25", "30000, 40000, 22500"})
    void evaluate_whenTheNewHouseCostsLessThanTheNetConsideration_exemptsItsProportion(String cost,
            String netConsideration, String exempt) {
        // Given — a gain of 30,000: the consideration 40,000 less the cost 10,000
        // When
        NodeResult result = result(2026, house(SOLD, LocalDate.of(2027, 1, 15), cost),
                bucket(netConsideration, "10000", LocalDate.of(2024, 5, 1), SOLD));

        // Then — 30,000 × cost / net consideration, to the paisa
        exemptionLine(result, Money.of("30000"), Money.of("30000").minus(Money.of(exempt)));
    }

    @Test
    void evaluate_whenTwoReinvestmentsNameTheSameTransfer_refusesTheComputation() {
        // Given — the golden's bucket, and two entries for its transfer date
        List<HouseReinvestment> two = List.of(house(SOLD, LocalDate.of(2027, 1, 15), "6000000"),
                house(SOLD, LocalDate.of(2027, 2, 1), "2000000"));

        // When / Then — neither is chosen for the other
        assertThatThrownBy(() -> result(2026, two, goldenBucket(SOLD)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2 reinvestments name the transfer");
    }

    @Test
    void evaluate_whenOneHouseIsClaimedForASaleOfTwoLots_measuresTheSaleOnce() {
        // Given — two long-term lots sold together, each 60,00,000 on a cost of 20,00,000, and one house of 60,00,000
        HouseReinvestment house = house(SOLD, LocalDate.of(2027, 1, 15), "6000000");

        // When
        NodeResult result = result(house, lot("6000000", "2000000", LocalDate.of(2023, 5, 1)),
                lot("6000000", "2000000", LocalDate.of(2024, 5, 1)));

        // Then — 80,00,000 × 60,00,000 / 1,20,00,000 = 40,00,000 exempt, 20,00,000 from each lot by its gain
        assertThat(exemptionFigures(result)).containsExactly(inOut("4000000", "2000000"), inOut("4000000", "2000000"));
        assertThat(exemptionLine(result, Money.of("4000000"), Money.of("2000000")).limitedBy()).hasValueSatisfying(
                reason -> assertThat(reason).contains("measured with the 2 buckets transferred on 2026-09-01"));
        assertThat(head(result).part(LONG_TERM_LISTED_EQUITY)).isEqualTo(Money.of("4000000"));
    }

    @Test
    void evaluate_whenTheSameSaleIsOneBucketOrTwo_chargesTheSameGain() {
        // Given — the sale of the case above, once as two lots and once as the single bucket portfolio makes when
        // both lots share a key
        HouseReinvestment house = house(SOLD, LocalDate.of(2027, 1, 15), "6000000");

        // When
        Money asTwo = head(result(house, lot("6000000", "2000000", LocalDate.of(2023, 5, 1)),
                lot("6000000", "2000000", LocalDate.of(2024, 5, 1)))).part(LONG_TERM_LISTED_EQUITY);
        Money asOne = head(result(house, lot("12000000", "4000000", LocalDate.of(2023, 5, 1))))
                .part(LONG_TERM_LISTED_EQUITY);

        // Then — 40,00,000 either way
        assertThat(asTwo).isEqualTo(Money.of("4000000"));
        assertThat(asOne).isEqualTo(asTwo);
    }

    @Test
    void evaluate_whenAShortTermLotIsSoldTheSameDay_leavesItOutOfTheMeasure() {
        // Given — a long-term lot of 60,00,000 on 20,00,000, a lot bought six months earlier on the same figures, and
        // a house of 60,00,000
        HouseReinvestment house = house(SOLD, LocalDate.of(2027, 1, 15), "6000000");

        // When
        NodeResult result = result(house, lot("6000000", "2000000", LocalDate.of(2023, 5, 1)),
                lot("6000000", "2000000", LocalDate.of(2026, 3, 1)));

        // Then — the row reaches the long-term lot alone; 60,00,000 ≥ its net consideration, so its 40,00,000 is
        // exempt, and the short-term 40,00,000 is charged
        assertThat(exemptionFigures(result)).containsExactly(inOut("4000000", "0"));
        assertThat(head(result).part(LONG_TERM_LISTED_EQUITY)).isEqualTo(Money.ZERO);
        assertThat(head(result).part(SHORT_TERM_LISTED_EQUITY)).isEqualTo(Money.of("4000000"));
    }

    @Test
    void evaluate_whenALotOfTheSaleMadeALoss_measuresTheSaleOnItsNetGain() {
        // Given — a lot gaining 40,00,000 (60,00,000 on 20,00,000), a lot losing 10,00,000 (30,00,000 on 40,00,000),
        // and a house of 45,00,000
        HouseReinvestment house = house(SOLD, LocalDate.of(2027, 1, 15), "4500000");

        // When
        NodeResult result = result(house, lot("6000000", "2000000", LocalDate.of(2023, 5, 1)),
                lot("3000000", "4000000", LocalDate.of(2024, 5, 1)));

        // Then — 30,00,000 × 45,00,000 / 90,00,000 = 15,00,000 exempt, all of it from the gaining lot; 25,00,000
        // less the 10,00,000 loss leaves 15,00,000, as the sale in one bucket would
        assertThat(exemptionFigures(result)).containsExactly(inOut("4000000", "2500000"));
        assertThat(head(result).part(LONG_TERM_LISTED_EQUITY)).isEqualTo(Money.of("1500000"));
    }

    @Test
    void evaluate_whenTheSalesLotsTogetherShowNoGain_exemptsNothing() {
        // Given — a lot gaining 10,00,000 (30,00,000 on 20,00,000), a lot losing 20,00,000 (20,00,000 on 40,00,000),
        // and a house of 50,00,000
        HouseReinvestment house = house(SOLD, LocalDate.of(2027, 1, 15), "5000000");

        // When
        NodeResult result = result(house, lot("3000000", "2000000", LocalDate.of(2023, 5, 1)),
                lot("2000000", "4000000", LocalDate.of(2024, 5, 1)));

        // Then — the sale lost 10,00,000, so nothing is exempt and the loss is carried
        assertThat(exemptionFigures(result)).containsExactly(inOut("1000000", "1000000"));
        assertThat(exemptionLine(result, Money.of("1000000"), Money.of("1000000")).limitedBy()).hasValueSatisfying(
                reason -> assertThat(reason).contains("show no gain"));
        assertThat(head(result).longTermLossLeft()).isEqualTo(Money.of("1000000"));
    }

    @Test
    void evaluate_whenTheExemptionSplitsUnevenly_givesTheLastLotTheRemainder() {
        // Given — three lots of 30,00,000 on 20,00,000 and a house of 10,00,001
        HouseReinvestment house = house(SOLD, LocalDate.of(2027, 1, 15), "1000001");

        // When
        NodeResult result = result(house, lot("3000000", "2000000", LocalDate.of(2023, 5, 1)),
                lot("3000000", "2000000", LocalDate.of(2023, 6, 1)), lot("3000000", "2000000", LocalDate.of(2023, 7, 1)));

        // Then — 30,00,000 × 10,00,001 / 90,00,000 = 3,33,333.67 exempt: 1,11,111.22 from the first two lots and the
        // remaining 1,11,111.23 from the last, so 26,66,666.33 is charged
        assertThat(exemptionFigures(result)).containsExactly(inOut("1000000", "888888.78"),
                inOut("1000000", "888888.78"), inOut("1000000", "888888.77"));
        assertThat(head(result).part(LONG_TERM_LISTED_EQUITY)).isEqualTo(Money.of("2666666.33"));
    }

    @Test
    void evaluate_whenAnotherLotIsSoldOnAnotherDate_leavesItCharged() {
        // Given — a lot sold on the claim's date and a lot sold a month later, each 60,00,000 on 20,00,000, and a
        // house of 60,00,000 claimed for the first sale
        HouseReinvestment house = house(SOLD, LocalDate.of(2027, 1, 15), "6000000");

        // When
        NodeResult result = result(house, lot("6000000", "2000000", LocalDate.of(2023, 5, 1)),
                bucket("6000000", "2000000", LocalDate.of(2023, 5, 1), LocalDate.of(2026, 10, 1)));

        // Then — the first sale's whole 40,00,000 is exempt; the second's 40,00,000 is charged
        assertThat(exemptionFigures(result)).containsExactly(inOut("4000000", "0"));
        assertThat(head(result).part(LONG_TERM_LISTED_EQUITY)).isEqualTo(Money.of("4000000"));
    }
}
