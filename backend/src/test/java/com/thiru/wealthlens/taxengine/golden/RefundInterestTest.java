package com.thiru.wealthlens.taxengine.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.FilingFacts;
import com.thiru.wealthlens.taxengine.pipeline.InterestAndFees;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Interest the Government adds to a refund, run end to end (2025 s.437(1) Table Sl. 1, 2, (2); Rules 2026 r.269; W-112b,
 * D-663), on {@code DR 13} T13-1's bill of 87,750 ({@code plans/W-089.md}) for TY 2026-27, due 31 July 2027.
 *
 * <p>Each figure is worked by hand in {@code plans/W-112b.md}: 0.5 % for every month or part of a month on the refund
 * rounded down to ₹100, none where the refund is less than 10 % of the tax (8,775). Months are counted from the first day
 * of the period to the grant, a part month whole.
 */
class RefundInterestTest {

    private static final GoldenCaseRunner RUNNER = new GoldenCaseRunner();

    private static final LocalDate ON_TIME = LocalDate.of(2027, 7, 15);

    private static final LocalDate GRANTED = LocalDate.of(2027, 9, 20);

    @Test
    void run_whenTaxAtSourceExceedsTheBillOnATimelyReturn_addsInterestFromAprilToTheGrant() {
        // Given — 1,00,000 at source: a refund of 12,250, at least 8,775; furnished on time, granted 20 September 2027
        EngineInputs inputs = filed("100000", ON_TIME, List.of(), Optional.of(GRANTED), LocalDate.of(2027, 7, 15));

        // When
        GoldenCaseRunner.ShippedPipelineRun run = RUNNER.run(inputs);

        // Then — 1 April to 20 September: 5 months and 20 days, 6 months; 12,200 × 0.5 % × 6 = 366, kept out of what is owed:
        // the payable stays the refund of 12,250
        assertThat(receivable(run)).containsExactly(Money.of("366"));
        assertThat(run.result().valueOf(NodeIds.ROUND_NETPAYABLE)).isEqualTo(MoneyValue.of(Money.of("-12250")));
        assertThat(run.result().annotations()).flatExtracting(annotation -> annotation.notices())
                .anySatisfy(notice -> assertThat(notice.message()).startsWith("Interest on a refund is income of the year"));
    }

    @Test
    void run_whenTheReturnIsLate_runsFromFurnishingOnTheRefundLeftAfterTheFee() {
        // Given — 1,20,000 at source, furnished 10 August 2027 after the due date: the s.428 fee of 5,000 (total income
        // 12,00,000 above 5,00,000) leaves a refund of 1,20,000 − 87,750 − 5,000 = 27,250
        EngineInputs inputs = filed("120000", LocalDate.of(2027, 8, 10), List.of(), Optional.of(GRANTED), LocalDate.of(2027, 8, 10));

        // When
        GoldenCaseRunner.ShippedPipelineRun run = RUNNER.run(inputs);

        // Then — 10 August to 20 September: 1 month and 11 days, 2 months; 27,200 × 0.5 % × 2 = 272
        assertThat(receivable(run)).containsExactly(Money.of("272"));
    }

    @Test
    void run_whenTheFeeTakesTheRefundBelowATenthOfTheTax_addsNone() {
        // Given — 1,00,000 at source, furnished late: 12,250 − 5,000 = 7,250, less than 8,775
        EngineInputs inputs = filed("100000", LocalDate.of(2027, 8, 10), List.of(), Optional.of(GRANTED), LocalDate.of(2027, 8, 10));

        // When
        GoldenCaseRunner.ShippedPipelineRun run = RUNNER.run(inputs);

        // Then
        assertThat(receivable(run)).isEmpty();
        assertThat(run.result().trace()).filteredOn(line -> line.rule().equals(RuleIds.INT_REFUND)).singleElement()
                .satisfies(line -> assertThat(line.limitedBy()).hasValueSatisfying(reason -> assertThat(reason)
                        .startsWith("a refund of 7250.00 is less than")));
    }

    @Test
    void run_whenNoGrantDateIsEntered_runsToTheAsOfDateAsIfGrantedThen() {
        // Given — the first case without a grant, computed on 14 November 2027
        EngineInputs inputs = filed("100000", ON_TIME, List.of(), Optional.empty(), LocalDate.of(2027, 11, 14));

        // When
        GoldenCaseRunner.ShippedPipelineRun run = RUNNER.run(inputs);

        // Then — 1 April to 14 November: 7 months and 14 days, 8 months; 12,200 × 0.5 % × 8 = 488
        assertThat(receivable(run)).containsExactly(Money.of("488"));
        assertThat(run.result().trace()).filteredOn(line -> line.rule().equals(RuleIds.INT_REFUND)).singleElement()
                .satisfies(line -> assertThat(line.limitedBy()).hasValueSatisfying(reason -> assertThat(reason)
                        .endsWith("as if granted on 2027-11-14")));
    }

    @Test
    void run_whenTaxWasPaidOnTheReturn_takesTheRefundOutOfItFirst() {
        // Given — 80,000 at source and 20,000 paid on the return on 1 July 2027: a refund of 12,250, under the 10,000 that
        // would make advance tax payable (87,750 − 80,000 = 7,750), so nothing else is owed
        EngineInputs inputs = filed("80000", ON_TIME,
                List.of(new FilingFacts.Payment(LocalDate.of(2027, 7, 1), Money.of("20000"))), Optional.of(GRANTED),
                LocalDate.of(2027, 7, 15));

        // When
        GoldenCaseRunner.ShippedPipelineRun run = RUNNER.run(inputs);

        // Then — all 12,250 out of the 20,000, from furnishing (the later of 15 July and 1 July) to 20 September: 2 months and
        // 6 days, 3 months; 12,200 × 0.5 % × 3 = 183
        assertThat(receivable(run)).containsExactly(Money.of("183"));
        assertThat(run.result().annotations()).flatExtracting(annotation -> annotation.notices())
                .anySatisfy(notice -> assertThat(notice.message()).startsWith("The Act does not say which payment"));
    }

    /** T13-1, furnished on {@code furnishedOn}, with this tax at source and on the return, computed on {@code asOf}. */
    private static EngineInputs filed(String taxAtSource, LocalDate furnishedOn, List<FilingFacts.Payment> onReturn,
            Optional<LocalDate> granted, LocalDate asOf) {
        EngineInputs plain = T13RebateAndSpecialRateTest.inputs(T13RebateAndSpecialRateTest.basicPay("175000"),
                T13RebateAndSpecialRateTest.gains("1600000", "500000"));
        FilingFacts filing = new FilingFacts(plain.filing().dueDate(), Optional.of(furnishedOn), List.of(),
                Money.of(taxAtSource), onReturn, Map.of(), granted);
        return new EngineInputs(plain.taxYear(), plain.regime(), TemporalContext.forTaxYear(TaxYearId.of(2026), asOf),
                plain.entered(), plain.assessee(), plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(),
                filing);
    }

    private static List<Money> receivable(GoldenCaseRunner.ShippedPipelineRun run) {
        return ((InterestAndFees) run.result().valueOf(NodeIds.INTEREST_AND_FEES)).items().stream()
                .filter(item -> item.kind() == InterestAndFees.Kind.RECEIVABLE).map(InterestAndFees.Item::amount).toList();
    }
}
