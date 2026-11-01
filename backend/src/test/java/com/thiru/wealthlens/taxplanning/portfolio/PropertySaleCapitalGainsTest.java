package com.thiru.wealthlens.taxplanning.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.thiru.wealthlens.portfolio.dto.PropertySaleRequest;
import com.thiru.wealthlens.portfolio.dto.enums.PropertyExpense;
import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.portfolio.repository.TradeOutcomeRepository;
import com.thiru.wealthlens.portfolio.service.CapitalGainsSummaryService;
import com.thiru.wealthlens.portfolio.service.PropertySaleService;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import com.thiru.wealthlens.taxengine.input.Assessee.ResidentialStatus;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.BucketFacts;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.Charge;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.ChargeSide;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.SideVenue;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.Individual;
import com.thiru.wealthlens.taxengine.node.TaxByBasisNode;
import com.thiru.wealthlens.taxengine.pipeline.CapitalGainsHead;
import com.thiru.wealthlens.taxengine.pipeline.HeadsAfterSetOff;
import com.thiru.wealthlens.taxengine.pipeline.IndexedGain;
import com.thiru.wealthlens.taxengine.pipeline.SpecialRateId;
import com.thiru.wealthlens.taxengine.pipeline.TaxByBasis;
import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsPart;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * A property sale from its request to the tax charged on it (W-088g9, D-434): its expenses reach the engine by code, the
 * rows class them by side, and the land cap's indexed measure lifts a purchase expense with the price, since by the
 * owner's ruling it is cost of acquisition (2025 s.72(8)(b); 1961 s.48 Explanation (iii)).
 *
 * <p>Every figure is worked by hand in {@code plans/W-088g9.md}, not taken from a run.
 */
@ExtendWith(MockitoExtension.class)
class PropertySaleCapitalGainsTest {

    private static final String EMAIL = "user@example.com";

    private static final CapitalGainsPart LONG_TERM_OTHER = new CapitalGainsPart("LONG_TERM_OTHER");

    private static final CapitalGainsPart SHORT_TERM_SLAB = new CapitalGainsPart("SHORT_TERM_SLAB");

    private static final RegimeCode FIRST_SCHEDULE_RATES = new RegimeCode("FIRST_SCHEDULE_RATES");

    private static final SpecialRateId LONG_OTHER = new SpecialRateId("RATE_CG_LONG_OTHER");

    private static final LocalDate BOUGHT_2015 = LocalDate.of(2015, 6, 1);

    private static final LocalDate SOLD_2025 = LocalDate.of(2025, 9, 1);

    @Mock
    private TradeOutcomeRepository tradeOutcomeRepository;

    private static PropertySaleRequest sale(LocalDate bought, double cost, Map<String, Double> acquisitionExpenses,
            LocalDate sold, double consideration, Map<String, Double> transferExpenses) {
        PropertySaleRequest request = new PropertySaleRequest();
        request.setPropertyName("Plot 7");
        request.setAcquiredOn(bought);
        request.setCostRupees(cost);
        request.setAcquisitionExpenses(acquisitionExpenses);
        request.setTransferredOn(sold);
        request.setConsiderationRupees(consideration);
        request.setTransferExpenses(transferExpenses);
        return request;
    }

    /** Case A of the plan: 40,00,000 with 3,60,000 of purchase expenses, sold for 1,00,00,000 with 1,20,000. */
    private static PropertySaleRequest caseA() {
        return sale(BOUGHT_2015, 4000000, Map.of("STAMP_DUTY", 280000.0, "REGISTRATION", 40000.0, "BROKERAGE", 40000.0),
                SOLD_2025, 10000000, Map.of("BROKERAGE", 100000.0, "LEGAL_FEE", 20000.0));
    }

    /** The head the row {@code request} saves makes, through the summary, the adapter and {@code CapitalGainsNode}. */
    private CapitalGainsHead headOf(PropertySaleRequest request) {
        new PropertySaleService(tradeOutcomeRepository).recordSale(UserMail.from(EMAIL), request);
        ArgumentCaptor<TradeOutcomeEntity> saved = ArgumentCaptor.forClass(TradeOutcomeEntity.class);
        verify(tradeOutcomeRepository).save(saved.capture());
        when(tradeOutcomeRepository.findByEmailAndSellDateBetween(eq(EMAIL), any(), any()))
                .thenReturn(List.of(saved.getValue()));
        CapitalGainsInput input = new CapitalGainsAdapter(new CapitalGainsSummaryService(tradeOutcomeRepository))
                .inputFor(EMAIL, TaxYearId.of(2025));
        return (CapitalGainsHead) CapitalGainsHarness.run(2025, input).value();
    }

    /** TY 2025-26's long-term tax on {@code head} beside 10,00,000 at slab rates, so no shortfall reaches the gain. */
    private static Money longTermTaxOn(CapitalGainsHead head) {
        TaxYearId taxYear = TaxYearId.of(2025);
        RulePack pack = new ClasspathRulePackProvider("rulepacks", Set.of(taxYear),
                new JsonPackAssembler(LockedInstruments.of(taxYear))).load(taxYear);
        EngineInputs plain = NodeHarness.plainTaxpayer();
        Individual person = new Individual(taxYear.endsOn().minusYears(40).minusMonths(6),
                ResidentialStatus.RESIDENT, Optional.empty(), Optional.empty());
        EngineInputs inputs = new EngineInputs(taxYear, FIRST_SCHEDULE_RATES,
                TemporalContext.forTaxYear(taxYear, taxYear.startsOn().plusMonths(5)), plain.entered(), person,
                plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(), plain.filing());
        Money totalIncome = Money.of("1000000").plus(head.total());
        TaxByBasis tax = (TaxByBasis) NodeHarness.with(pack).regime(FIRST_SCHEDULE_RATES).inputs(inputs)
                .given(NodeIds.ROUND_TOTALINCOME, totalIncome)
                .given(NodeIds.SETOFF, new HeadsAfterSetOff(totalIncome, head.parts(), head.indexedGains()))
                .run(new TaxByBasisNode()).value();
        return tax.at(LONG_OTHER);
    }

    /** The 2015 plot, 40,00,000 → 1,00,00,000, carrying {@code charges}, as the engine receives it. */
    private static CapitalGainsHead headOfPlot(int year, Charge... charges) {
        BucketFacts facts = new BucketFacts("LAND_OR_BUILDING", "UNKNOWN", "UNKNOWN", "PURCHASE", Optional.empty(),
                BOUGHT_2015, LocalDate.of(year, 9, 1), SideVenue.UNRECORDED, SideVenue.UNRECORDED);
        return (CapitalGainsHead) CapitalGainsHarness.run(year, CapitalGainsHarness.inputOf(new CapitalGainsInput.Bucket(
                Money.of("10000000"), Money.of("4000000"), List.of(charges), facts, Optional.empty()))).value();
    }

    @Test
    void headOfASale_whenExpensesAreCoded_nettsThemFromTheGainByTheirSide() {
        // When
        CapitalGainsHead head = headOf(caseA());

        // Then — 1,00,00,000 − 1,20,000 − 40,00,000 − 3,60,000 = 55,20,000
        assertThat(head.part(LONG_TERM_OTHER)).isEqualTo(Money.of("5520000"));
        assertThat(head.total()).isEqualTo(Money.of("5520000"));
    }

    @Test
    void indexedMeasure_whenAPurchaseExpenseIsCost_indexesItWithThePrice() {
        // When
        CapitalGainsHead head = headOf(caseA());

        // Then — 43,60,000 × 376 ÷ 254 = 64,54,173.23; 1,00,00,000 − 1,20,000 − 64,54,173.23 = 34,25,826.77. The price
        // alone would give 40,00,000 × 376 ÷ 254 = 59,21,259.84 and a measure of 35,98,740.16
        assertThat(head.indexedGains(LONG_TERM_OTHER)).extracting(IndexedGain::indexedGain)
                .containsExactly(Money.of("3425826.77"));
    }

    @Test
    void taxOnTheSale_whenExpensesAreIndexed_theCapBinds() {
        // Given
        CapitalGainsHead head = headOf(caseA());

        // When
        Money tax = longTermTaxOn(head);

        // Then — 12.5 % of 55,20,000 is 6,90,000 against 20 % of 34,25,826.77 = 6,85,165.35; the lesser is charged
        assertThat(tax).isEqualTo(Money.of("685165.35"));
    }

    @Test
    void headOfASale_whenHeldSevenMonths_isShortTermAfterItsExpenses() {
        // Given — 25,00,000 − 50,000 − 20,00,000 − 1,00,000 = 3,50,000
        PropertySaleRequest request = sale(LocalDate.of(2025, 1, 10), 2000000, Map.of("STAMP_DUTY", 100000.0),
                SOLD_2025, 2500000, Map.of("BROKERAGE", 50000.0));

        // When
        CapitalGainsHead head = headOf(request);

        // Then
        assertThat(head.part(SHORT_TERM_SLAB)).isEqualTo(Money.of("350000"));
        assertThat(head.part(LONG_TERM_OTHER)).isEqualTo(Money.ZERO);
    }

    @Test
    void indexedMeasure_whenASaleExpenseIsCharged_leavesItAtFaceValue() {
        // When — 1,00,000 of brokerage on the sale
        CapitalGainsHead head = headOfPlot(2025, new Charge(ChargeSide.TRANSFER, "BROKERAGE", Money.of("100000")));

        // Then — only the 40,00,000 is lifted: 1,00,00,000 − 1,00,000 − 59,21,259.84 = 39,78,740.16
        assertThat(head.indexedGains(LONG_TERM_OTHER)).extracting(IndexedGain::indexedGain)
                .containsExactly(Money.of("3978740.16"));
    }

    @Test
    void indexedMeasure_whenABuySideChargeIsNotDeducted_doesNotIndexIt() {
        // When — securities transaction tax is not cost (s.48 sixth proviso; s.72(3)(b)), whatever side it sits on
        CapitalGainsHead head = headOfPlot(2025, new Charge(ChargeSide.ACQUISITION, "STT", Money.of("100000")));

        // Then — 1,00,00,000 − 59,21,259.84 = 40,78,740.16, as with no charge at all
        assertThat(head.indexedGains(LONG_TERM_OTHER)).extracting(IndexedGain::indexedGain)
                .containsExactly(Money.of("4078740.16"));
    }

    static Stream<Arguments> everyExpenseBySideAndYear() {
        List<Arguments> cases = new ArrayList<>();
        for (TaxYearId year : ClasspathRulePackProvider.shippedYears("rulepacks")) {
            for (PropertyExpense expense : PropertyExpense.values()) {
                for (ChargeSide side : ChargeSide.values()) {
                    cases.add(Arguments.of(expense.name(), side, year.startYear()));
                }
            }
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "{0} on the {1}, TY {2}")
    @MethodSource("everyExpenseBySideAndYear")
    void head_whenAnExpenseIsCharged_aRowDeductsIt(String code, ChargeSide side, int year) {
        // When — the engine refuses a code no row covers, so a code added to the enum without a row fails here
        CapitalGainsHead head = headOfPlot(year, new Charge(side, code, Money.of("1000")));

        // Then — a deduction, not NOT_DEDUCTED: 1,00,00,000 − 40,00,000 − 1,000 = 59,99,000
        assertThat(head.total()).isEqualTo(Money.of("5999000"));
    }
}
