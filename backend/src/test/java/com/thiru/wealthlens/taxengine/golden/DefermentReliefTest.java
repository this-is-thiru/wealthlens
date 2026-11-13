package com.thiru.wealthlens.taxengine.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.input.CapitalGainsInput;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.BucketFacts;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.SideVenue;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.FilingFacts;
import com.thiru.wealthlens.taxengine.input.OtherSourceItem;
import com.thiru.wealthlens.taxengine.input.OtherSourceKind;
import com.thiru.wealthlens.taxengine.pipeline.InterestAndFees;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.test.CapitalGainsHarness;
import com.thiru.wealthlens.taxengine.trace.Notice;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The relief from deferment interest, run end to end (2025 s.425(4); W-112a, D-660), on {@code DR 13} T13-1's figures:
 * ₹1,00,000 of salary after the standard deduction and ₹11,00,000 of long-term listed-equity gains make a bill of 87,750
 * ({@code plans/W-089.md}), and the salary alone is within the 4,00,000 nil band, so the bill without the gain is nil.
 *
 * <p>Every figure is worked by hand in {@code plans/W-112a.md}, from {@code INT-DEFERMENT}'s shares and rates and
 * {@code INT-CALCULATION}'s ₹100 base: at 15 June, 15 September and 15 December the shortfall is 15, 45 and 75 per cent of
 * the tax due, bases 13,100, 39,400 and 65,800, interest at 3 per cent 393, 1,182 and 1,974.
 */
class DefermentReliefTest {

    private static final GoldenCaseRunner RUNNER = new GoldenCaseRunner();

    private static final LocalDate ACQUIRED = LocalDate.of(2024, 4, 1);

    private static final LocalDate FURNISHED = LocalDate.of(2027, 7, 15);

    private static final LocalDate PAID = LocalDate.of(2027, 3, 14);

    private static final SideVenue SOLD_ON_NSE = new SideVenue(Optional.of("NSE"), Optional.empty());

    @Test
    void run_whenTheGainArisesAfterDecemberAndItsTaxIsPaidInMarch_chargesNoDefermentInterest() {
        // Given — sold on 10 January 2027, the whole 87,750 paid on 14 March
        EngineInputs inputs = filed(gainsSoldOn(LocalDate.of(2027, 1, 10)), List.of(), "87750");

        // When
        GoldenCaseRunner.ShippedPipelineRun run = RUNNER.run(inputs);

        // Then — at each of the three dates the tax due is 87,750 less the 87,750 left out, so nothing falls short; at
        // 15 March the gain has arisen and all of it was paid
        assertThat(run.result().valueOf(NodeIds.TAX_NETPAYABLE)).isEqualTo(MoneyValue.of(Money.of("87750")));
        assertThat(byRule(run)).doesNotContainKey(RuleIds.INT_DEFERMENT);
        assertThat(run.result().trace())
                .filteredOn(line -> line.rule().equals(RuleIds.INT_DEFERMENT))
                .extracting(line -> line.limitedBy().orElseThrow())
                .containsExactly(
                        "by 2026-06-15, paid enough; relieved 87750.00: tax on income arising after 2026-06-15, paid after it"
                                + " by 2027-03-31",
                        "by 2026-09-15, paid enough; relieved 87750.00: tax on income arising after 2026-09-15, paid after it"
                                + " by 2027-03-31",
                        "short by 2026-12-15; relieved 87750.00: tax on income arising after 2026-12-15, paid after it by"
                                + " 2027-03-31",
                        "short by 2027-03-15");
    }

    @Test
    void run_whenTheGainsTaxIsNotPaidInFullByMarch_chargesEveryDateAsBefore() {
        // Given — the same sale, but only 80,000 paid on 14 March: less than the 87,750 left out, so (b) fails
        EngineInputs inputs = filed(gainsSoldOn(LocalDate.of(2027, 1, 10)), List.of(), "80000");

        // When
        GoldenCaseRunner.ShippedPipelineRun run = RUNNER.run(inputs);

        // Then — 393 + 1,182 + 1,974, and at 15 March 1 per cent of 7,750 short, base 7,700: 77; 3,626. The 90 per cent
        // test is met (80,000 against 78,975), so no shortfall interest
        assertThat(byRule(run)).containsExactlyEntriesOf(Map.of(RuleIds.INT_DEFERMENT, Money.of("3626")));
    }

    @Test
    void run_whenTheGainArisesOnAnInstalmentDate_isNotRelievedAtThatDate() {
        // Given — sold on 15 December 2026 itself, the whole bill paid on 14 March
        EngineInputs inputs = filed(gainsSoldOn(LocalDate.of(2026, 12, 15)), List.of(), "87750");

        // When
        GoldenCaseRunner.ShippedPipelineRun run = RUNNER.run(inputs);

        // Then — relieved at 15 June and 15 September; at 15 December the gain has arisen: 3 % of 65,800 = 1,974
        assertThat(byRule(run)).containsExactlyEntriesOf(Map.of(RuleIds.INT_DEFERMENT, Money.of("1974")));
    }

    @Test
    void run_whenADividendIsReceivedAfterDecember_chargesNoDefermentInterest() {
        // Given — salary 1,00,000 and a dividend of 14,00,000 received on 20 December 2026, the bill paid on 14 March.
        // Total income 15,00,000: 5 % of 4,00,000 + 10 % of 4,00,000 + 15 % of 3,00,000 = 1,05,000 at the TY 2026-27
        // slabs (RATE-SLAB-NEW-TAX-REGIME), no rebate above 12,00,000, cess 4,200: 1,09,200
        EngineInputs inputs = filed(CapitalGainsHarness.inputOf(),
                List.of(dividend(Optional.of(LocalDate.of(2026, 12, 20)))), "109200");

        // When
        GoldenCaseRunner.ShippedPipelineRun run = RUNNER.run(inputs);

        // Then
        assertThat(run.result().valueOf(NodeIds.TAX_NETPAYABLE)).isEqualTo(MoneyValue.of(Money.of("109200")));
        assertThat(byRule(run)).doesNotContainKey(RuleIds.INT_DEFERMENT);
    }

    @Test
    void run_whenADividendCarriesNoDate_chargesTheDatesAndSaysWhy() {
        // Given — the same dividend entered without the date it was received
        EngineInputs inputs = filed(CapitalGainsHarness.inputOf(), List.of(dividend(Optional.empty())), "109200");

        // When
        GoldenCaseRunner.ShippedPipelineRun run = RUNNER.run(inputs);

        // Then — 15 %, 45 %, 75 % of 1,09,200: bases 16,300, 49,100, 81,900; at 3 %: 489 + 1,473 + 2,457 = 4,419
        assertThat(byRule(run)).containsExactlyEntriesOf(Map.of(RuleIds.INT_DEFERMENT, Money.of("4419")));
        assertThat(run.result().annotations())
                .flatExtracting(annotation -> annotation.notices())
                .extracting(Notice::message)
                .anySatisfy(message -> assertThat(message).startsWith("A dividend entered without the date it was received"));
    }

    /** T13-1's salary, these gains and other-source items, furnished on 15 July 2027 with {@code paid} paid on 14 March. */
    private static EngineInputs filed(CapitalGainsInput gains, List<OtherSourceItem> items, String paid) {
        EngineInputs plain = T13RebateAndSpecialRateTest.inputs(T13RebateAndSpecialRateTest.basicPay("175000"), gains);
        FilingFacts filing = new FilingFacts(plain.filing().dueDate(), Optional.of(FURNISHED),
                List.of(new FilingFacts.Payment(PAID, Money.of(paid))), Money.ZERO, List.of(), Map.of(), Optional.empty());
        return new EngineInputs(plain.taxYear(), plain.regime(), plain.temporal(), plain.entered().withOtherSourceItems(items),
                plain.assessee(), plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(), filing);
    }

    /** T13-1's lot: bought for 5,00,000 on 1 April 2024 and sold on NSE for 16,00,000, STT paid, on {@code soldOn}. */
    private static CapitalGainsInput gainsSoldOn(LocalDate soldOn) {
        BucketFacts facts = new BucketFacts("EQUITY", "PAID", "PAID", "PURCHASE", Optional.empty(), ACQUIRED, soldOn,
                SideVenue.UNRECORDED, SOLD_ON_NSE);
        return CapitalGainsHarness.inputOf(new CapitalGainsInput.Bucket(Money.of("1600000"), Money.of("500000"),
                List.of(), facts, Optional.empty()));
    }

    private static OtherSourceItem dividend(Optional<LocalDate> receivedOn) {
        return new OtherSourceItem(OtherSourceKind.DIVIDEND, Money.of("1400000"), Money.ZERO, Money.ZERO, Money.ZERO,
                false, receivedOn);
    }

    private static Map<RuleId, Money> byRule(GoldenCaseRunner.ShippedPipelineRun run) {
        Map<RuleId, Money> amounts = new HashMap<>();
        ((InterestAndFees) run.result().valueOf(NodeIds.INTEREST_AND_FEES)).items()
                .forEach(item -> amounts.put(item.rule(), item.amount()));
        return amounts;
    }
}
