package com.thiru.wealthlens.taxengine.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.input.CapitalGainsInput;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.BucketFacts;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.SideVenue;
import com.thiru.wealthlens.taxengine.input.EmployerKind;
import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.SalaryComponent;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.SpecialRateId;
import com.thiru.wealthlens.taxengine.pipeline.TaxByBasis;
import com.thiru.wealthlens.taxengine.rules.generated.ComponentCodes;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.test.CapitalGainsHarness;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * {@code DR 13} T13-1, run end to end (W-089): a resident individual's ₹12,00,000 of total income — ₹1,00,000 of
 * salary after the standard deduction and ₹11,00,000 of long-term listed-equity gains — keeps its special-rate tax,
 * because s.156(3) caps the new regime's rebate at the income-tax payable at the s.202(1) slab rates, which is nil.
 * A rebate taken against the whole tax would have made the bill nil, and that answer "looks right, the arithmetic is
 * sound at every step, and [is] wrong by the whole bill" ({@code 13-edge-cases.md} R-5).
 *
 * <p>Every figure is worked by hand in {@code plans/W-089.md} from the pack rows the case's own terms name, never
 * taken from a run: the 4,00,000 nil band leaves 3,00,000 unused, which comes off the gain (11,00,000 → 8,00,000);
 * 12½ per cent above 1,25,000 of it is 84,375; the rebate is nil; cess at 4 per cent is 3,375; net payable 87,750.
 */
class T13RebateAndSpecialRateTest {

    private static final GoldenCaseRunner RUNNER = new GoldenCaseRunner();

    private static final TaxYearId TY_2026_27 = TaxYearId.of(2026);

    private static final SpecialRateId LONG_LISTED = new SpecialRateId("RATE_CG_LONG_LISTED_EQUITY");

    private static final LocalDate ACQUIRED = LocalDate.of(2024, 4, 1);

    private static final LocalDate SOLD = LocalDate.of(2026, 9, 1);

    private static final SideVenue SOLD_ON_NSE = new SideVenue(Optional.of("NSE"), Optional.empty());

    @Test
    void run_whenT13_1MixesSalaryAndLongTermGains_chargesTheGainsAndGivesNoRebate() {
        // Given — DR 13 T13-1: salary 1,00,000 after the standard deduction and long-term gains of 11,00,000. The
        // 75,000 deduction leaves gross basic pay at 1,75,000; the gain is 16,00,000 less a cost of 5,00,000
        EngineInputs inputs = inputs(basicPay("175000"), gains("1600000", "500000"));

        // When
        GoldenCaseRunner.ShippedPipelineRun run = RUNNER.run(inputs);

        // Then — 1,00,000 + 11,00,000 of total income, all through the real heads, total income and rate basis
        assertThat(run.result().valueOf(NodeIds.ROUND_TOTALINCOME))
                .isEqualTo(MoneyValue.of(Money.of("1200000")));

        // Then — the 4,00,000 nil band leaves 3,00,000 unused over the salary, and it comes off the gain
        assertThat(run.result().trace())
                .filteredOn(line -> line.rule().equals(RuleIds.RATE_CG_SHORTFALL_ORDER))
                .singleElement()
                .satisfies(line -> {
                    assertThat(line.kind()).isEqualTo(StepKind.MEASURE);
                    assertThat(line.in()).isEqualTo(MoneyValue.of(Money.of("1100000")));
                    assertThat(line.out()).isEqualTo(MoneyValue.of(Money.of("800000")));
                });

        // Then — nothing at slab rates, and 12½ % of 8,00,000 less 1,25,000 = 84,375 at the special rate
        TaxByBasis byBasis = (TaxByBasis) run.result().valueOf(NodeIds.TAX_BYBASIS);
        assertThat(byBasis.atSlabRates()).isEqualTo(Money.ZERO);
        assertThat(byBasis.at(LONG_LISTED)).isEqualTo(Money.of("84375"));

        // Then — the rebate is nil, and it says why: 60,000 is well within its ceiling of 60,000 at this total income,
        // but s.156(3) holds it to the slab-rate tax, which is nothing
        assertThat(run.result().trace())
                .filteredOn(line -> line.rule().equals(RuleIds.REBATE_NEW_TAX_REGIME))
                .singleElement()
                .satisfies(line -> {
                    assertThat(line.kind()).isEqualTo(StepKind.RELIEF);
                    assertThat(line.out()).isEqualTo(MoneyValue.of(Money.ZERO));
                    assertThat(line.limitedBy())
                            .hasValueSatisfying(reason -> assertThat(reason).contains("tax at slab rates"));
                });

        // Then — the special-rate tax stands untouched, which is the interaction the case exists to guard
        assertThat(run.result().valueOf(NodeIds.TAX_AFTERREBATE)).isEqualTo(byBasis);

        // Then — 84,375 plus cess of 3,375, not the nil a rebate on the whole tax would have given
        assertThat(run.result().valueOf(NodeIds.TAX_CESS)).isEqualTo(MoneyValue.of(Money.of("3375")));
        assertThat(run.result().valueOf(NodeIds.ROUND_NETPAYABLE)).isEqualTo(MoneyValue.of(Money.of("87750")));
    }

    /** One employment whose only pay is basic pay. */
    private static Employment basicPay(String rupees) {
        return new Employment(List.of(new SalaryComponent(ComponentCodes.BASIC, Money.of(rupees))), false,
                EmployerKind.OTHER, false, Optional.empty());
    }

    /** One long-term listed-equity lot bought for {@code cost} and sold on NSE for {@code consideration}, STT paid. */
    private static CapitalGainsInput gains(String consideration, String cost) {
        BucketFacts facts = new BucketFacts("EQUITY", "PAID", "PAID", "PURCHASE", Optional.empty(), ACQUIRED, SOLD,
                SideVenue.UNRECORDED, SOLD_ON_NSE);
        return CapitalGainsHarness.inputOf(new CapitalGainsInput.Bucket(Money.of(consideration), Money.of(cost),
                List.of(), facts, Optional.empty()));
    }

    /** The harness's resident taxpayer in TY 2026-27, in the default regime, with one employment and these gains. */
    private static EngineInputs inputs(Employment employment, CapitalGainsInput gains) {
        EngineInputs plain = NodeHarness.plainTaxpayer();
        return new EngineInputs(TY_2026_27, plain.regime(),
                TemporalContext.forTaxYear(TY_2026_27, TY_2026_27.startsOn().plusMonths(5)), entered(employment),
                plain.assessee(), plain.family(), plain.ledgers(), plain.elections(), gains, plain.filing());
    }

    /** Nothing entered but the employment. */
    private static EnteredFacts entered(Employment employment) {
        return new EnteredFacts(List.of(employment), List.of(), List.of(), List.of(), Optional.empty(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                Optional.empty());
    }
}
