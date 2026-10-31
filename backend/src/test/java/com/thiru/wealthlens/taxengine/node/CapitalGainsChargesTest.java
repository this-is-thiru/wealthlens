package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.CapitalGainsInput;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.BucketFacts;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.Charge;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.ChargeSide;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.SideVenue;
import com.thiru.wealthlens.taxengine.pipeline.CapitalGainsHead;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsPart;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.test.CapitalGainsHarness;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The gain a bucket's charges leave (2025 s.72; 1961 s.48; W-162a, D-375): the buy's charges are cost, the sale's
 * transfer expenditure, securities transaction tax and a hand-entered charge are not deducted, and a charge no row
 * covers is refused. Expected figures are worked by hand in {@code plans/W-162a.md}.
 */
class CapitalGainsChargesTest {

    private static final CapitalGainsPart LONG_TERM_LISTED_EQUITY = new CapitalGainsPart("LONG_TERM_LISTED_EQUITY");

    /** Long-term listed equity, STT paid on both sides, sold on NSE — the part rows take it at the listed rate. */
    private static CapitalGainsInput.Bucket listedEquity(List<Charge> charges) {
        LocalDate acquiredOn = LocalDate.parse("2020-06-01");
        BucketFacts facts = new BucketFacts("EQUITY", "PAID", "PAID", "PURCHASE", Optional.empty(), acquiredOn,
                acquiredOn.plusYears(2), SideVenue.UNRECORDED, new SideVenue(Optional.of("NSE"), Optional.empty()));
        return new CapitalGainsInput.Bucket(Money.of("100000"), Money.of("80000"), charges, facts, Optional.empty());
    }

    private static Charge charge(ChargeSide side, String code, String amount) {
        return new Charge(side, code, Money.of(amount));
    }

    private static final List<Charge> CONTRACT_NOTES = List.of(
            charge(ChargeSide.ACQUISITION, "BROKERAGE", "20"),
            charge(ChargeSide.ACQUISITION, "STAMP_DUTY", "12"),
            charge(ChargeSide.ACQUISITION, "STT", "80"),
            charge(ChargeSide.TRANSFER, "BROKERAGE", "20"),
            charge(ChargeSide.TRANSFER, "GST", "3.60"),
            charge(ChargeSide.TRANSFER, "STT", "100"),
            charge(ChargeSide.TRANSFER, "MISC", "10"));

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenTheBucketBoreCharges_deductsTheCostAndTransferChargesOnly(int year) {
        // When
        CapitalGainsHead head = (CapitalGainsHead) CapitalGainsHarness.run(year,
                CapitalGainsHarness.inputOf(listedEquity(CONTRACT_NOTES))).value();

        // Then — 1,00,000 − 80,000 = 20,000; deducted: buy brokerage 20 + stamp duty 12 (cost), sale brokerage 20 +
        // GST 3.60 (transfer expenditure) = 55.60; STT 80 + 100 and misc 10 are not: 20,000 − 55.60 = 19,944.40
        assertThat(head.part(LONG_TERM_LISTED_EQUITY)).isEqualTo(Money.of("19944.40"));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenTheBucketBoreCharges_tracesEachAgainstItsRow(int year) {
        // When
        NodeResult result = CapitalGainsHarness.run(year, CapitalGainsHarness.inputOf(listedEquity(CONTRACT_NOTES)));

        // Then — one line per charge, in the bucket's order: in the amount, out what was deducted
        List<TraceLine> lines = result.trace().stream()
                .filter(line -> line.rule().value().startsWith("CG-CHARGE-")).toList();
        assertThat(lines).extracting(TraceLine::rule).containsExactly(RuleIds.CG_CHARGE_COST, RuleIds.CG_CHARGE_COST,
                RuleIds.CG_CHARGE_STT, RuleIds.CG_CHARGE_TRANSFER, RuleIds.CG_CHARGE_TRANSFER, RuleIds.CG_CHARGE_STT,
                RuleIds.CG_CHARGE_MISC);
        assertThat(lines).extracting(TraceLine::out).containsExactly(MoneyValue.of(Money.of("20")),
                MoneyValue.of(Money.of("12")), MoneyValue.of(Money.ZERO), MoneyValue.of(Money.of("20")),
                MoneyValue.of(Money.of("3.60")), MoneyValue.of(Money.ZERO), MoneyValue.of(Money.ZERO));
        assertThat(lines.get(2).limitedBy()).contains("STT on the ACQUISITION: NOT_DEDUCTED");
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenAChargeNoRowCovers_isRefusedNamingIt(int year) {
        // Given — a code the rows have not been given
        CapitalGainsInput input = CapitalGainsHarness.inputOf(listedEquity(List.of(
                charge(ChargeSide.TRANSFER, "NEW_FEE", "5"))));

        // When / Then — neither counted nor dropped unseen
        assertThatThrownBy(() -> CapitalGainsHarness.run(year, input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The charge NEW_FEE on the TRANSFER")
                .hasMessageContaining("RS-CG-CHARGE");
    }
}
