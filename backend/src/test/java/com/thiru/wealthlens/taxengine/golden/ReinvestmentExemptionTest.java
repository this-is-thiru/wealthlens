package com.thiru.wealthlens.taxengine.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.input.CapitalGainsInput;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.BucketFacts;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.SideVenue;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.HouseReinvestment;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.SpecialRateId;
import com.thiru.wealthlens.taxengine.pipeline.TaxByBasis;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The house-reinvestment exemption end to end (2025 s.86; 1961 s.54F; W-088i): a resident individual's ₹80,00,000
 * long-term gain on listed equity, ₹60,00,000 of which buys a house, is charged on ₹40,00,000 rather than on the whole.
 *
 * <p>Every figure is worked by hand from the pack rows the case's own terms name, never taken from a run — and one
 * of them is not the one {@code plans/W-088i.md}'s table states. The gain is ₹1,20,00,000 less ₹40,00,000 = ₹80,00,000;
 * the house's ₹60,00,000 against a net consideration of ₹1,20,00,000 exempts 80,00,000 × 60,00,000 / 1,20,00,000 =
 * ₹40,00,000, leaving ₹40,00,000. Total income is then ₹40,00,000 and nothing else stands in the way of the
 * zero-rate band of ₹4,00,000 ({@code RATE-SLAB-NEW-TAX-REGIME}), so {@code RATE-CG-SHORTFALL-ORDER} shifts the whole
 * ₹4,00,000 onto the special-rate gain (2025 s.196(2); 1961 s.112A(2) proviso), leaving ₹36,00,000, which the
 * listed-equity ladder
 * taxes at 12½ per cent above ₹1,25,000: 12½ % × 34,75,000 = ₹4,34,375. The rebate is nil (total income above
 * ₹12,00,000), the surcharge nil (below ₹50,00,000), cess 4 % = ₹17,375, so net payable is ₹4,51,750.
 */
class ReinvestmentExemptionTest {

    private static final GoldenCaseRunner RUNNER = new GoldenCaseRunner();

    private static final TaxYearId TY_2026_27 = TaxYearId.of(2026);

    private static final SpecialRateId LONG_LISTED = new SpecialRateId("RATE_CG_LONG_LISTED_EQUITY");

    private static final LocalDate ACQUIRED = LocalDate.of(2024, 5, 1);

    private static final LocalDate SOLD = LocalDate.of(2026, 9, 1);

    private static final LocalDate HOUSE_BOUGHT = LocalDate.of(2027, 1, 15);

    private static final SideVenue SOLD_ON_NSE = new SideVenue(Optional.of("NSE"), Optional.empty());

    @Test
    void run_whenALongTermEquityGainBuysAHouse_exemptsTheCostsShareAndChargesTheRest() {
        // Given — one lot bought 2024-05-01 for 40,00,000 and sold 2026-09-01 for 1,20,00,000, STT paid both
        // sides, and a house bought 2027-01-15 for 60,00,000
        EngineInputs inputs = inputs(new HouseReinvestment(SOLD, HOUSE_BOUGHT, false, Money.of("6000000"), 0, 0));

        // When
        GoldenCaseRunner.ShippedPipelineRun run = RUNNER.run(inputs);

        // Then — the exemption line: 80,00,000 in, 40,00,000 out
        assertThat(run.result().trace())
                .filteredOn(line -> line.rule().equals(RuleIds.CG_EXEMPT_RESIDENTIAL_HOUSE)
                        && line.kind() == StepKind.EXEMPTION)
                .singleElement()
                .satisfies(line -> {
                    assertThat(line.in()).isEqualTo(MoneyValue.of(Money.of("8000000")));
                    assertThat(line.out()).isEqualTo(MoneyValue.of(Money.of("4000000")));
                });

        // Then — the zero-rate band stands unexhausted and the shortfall order puts it on the gain
        assertThat(run.result().trace())
                .filteredOn(line -> line.rule().equals(RuleIds.RATE_CG_SHORTFALL_ORDER))
                .singleElement()
                .satisfies(line -> {
                    assertThat(line.in()).isEqualTo(MoneyValue.of(Money.of("4000000")));
                    assertThat(line.out()).isEqualTo(MoneyValue.of(Money.of("3600000")));
                });

        // Then — total income 40,00,000, and 12½ % of 36,00,000 less 1,25,000 at the special rate
        assertThat(run.result().valueOf(NodeIds.ROUND_TOTALINCOME))
                .isEqualTo(MoneyValue.of(Money.of("4000000")));
        TaxByBasis byBasis = (TaxByBasis) run.result().valueOf(NodeIds.TAX_BYBASIS);
        assertThat(byBasis.at(LONG_LISTED)).isEqualTo(Money.of("434375"));

        // Then — no rebate at this total income, cess of 17,375, and 4,51,750 to pay
        assertThat(run.result().valueOf(NodeIds.TAX_CESS)).isEqualTo(MoneyValue.of(Money.of("17375")));
        assertThat(run.result().valueOf(NodeIds.ROUND_NETPAYABLE)).isEqualTo(MoneyValue.of(Money.of("451750")));
    }

    /** The harness's resident taxpayer in TY 2026-27, the default regime, one long-term equity lot and one house. */
    private static EngineInputs inputs(HouseReinvestment reinvestment) {
        BucketFacts facts = new BucketFacts("EQUITY", "PAID", "PAID", "PURCHASE", Optional.empty(), ACQUIRED, SOLD,
                SideVenue.UNRECORDED, SOLD_ON_NSE);
        CapitalGainsInput gains = new CapitalGainsInput(
                List.of(new CapitalGainsInput.Bucket(Money.of("12000000"), Money.of("4000000"), List.of(), facts,
                        Optional.empty())),
                java.time.Instant.parse("2026-04-15T10:00:00Z"), "run-1");
        EngineInputs plain = NodeHarness.plainTaxpayer();
        EnteredFacts entered = EnteredFacts.none().withHouseReinvestments(List.of(reinvestment));
        return new EngineInputs(TY_2026_27, plain.regime(),
                TemporalContext.forTaxYear(TY_2026_27, TY_2026_27.startsOn()), entered,
                plain.assessee(), plain.family(), plain.ledgers(), plain.elections(), gains, plain.filing());
    }
}
