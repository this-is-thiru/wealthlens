package com.thiru.wealthlens.taxengine.rules.relief;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The relief family ({@code DR 10} E5, D-073). Law figures are S2 s.156's; every expected amount is
 * worked by hand in {@code plans/W-048.md}, not taken from a run.
 */
class ReliefShapeTest {

    private static ReliefContext context(String totalIncome, String taxBeforeRelief) {
        return new ReliefContext(Money.of(totalIncome), Money.of(taxBeforeRelief), Optional.empty());
    }

    @Test
    void flatWithCeiling_whenIncomeIsAtTheCeiling_givesTheWholeTaxUpToTheMaximum() {
        // Given — s.156(2)(a): "does not exceed twelve lakh rupees, 100% … or ₹ 60000, whichever is less"
        ReliefShape rebate = new FlatWithCeiling(Money.of("60000"), Money.of("1200000"));

        // When
        ReliefResult result = rebate.apply(context("1200000", "60000"));

        // Then
        assertThat(result.amount()).isEqualTo(Money.of("60000"));
    }

    @Test
    void flatWithCeiling_whenIncomeIsOneRupeeAboveTheCeiling_givesNothing() {
        // When
        ReliefResult result = new FlatWithCeiling(Money.of("60000"), Money.of("1200000"))
                .apply(context("1200001", "60000.10"));

        // Then
        assertThat(result.amount()).isEqualTo(Money.ZERO);
    }

    @Test
    void flatWithCeiling_whenTaxIsBelowTheMaximum_givesTheWholeTax() {
        // Given — s.156(1): "100% of income-tax payable or ₹ 12500, whichever is less"; tax at ₹4,90,000
        // on the under-60 ladder is 5% of 2,40,000 = 12,000
        // When
        ReliefResult result = new FlatWithCeiling(Money.of("12500"), Money.of("500000"))
                .apply(context("490000", "12000"));

        // Then
        assertThat(result.amount()).isEqualTo(Money.of("12000"));
    }

    @Test
    void excessOverThreshold_whenTaxExceedsTheIncomeAboveTheThreshold_givesTheDifference() {
        // Given — s.156(2)(b); tax at ₹12,10,000 = 60,000 + 15% of 10,000 = 61,500; 61,500 − 10,000
        // When
        ReliefResult result = new ExcessOverThreshold(Money.of("1200000")).apply(context("1210000", "61500"));

        // Then
        assertThat(result.amount()).isEqualTo(Money.of("51500"));
    }

    @Test
    void excessOverThreshold_whenTheIncomeAboveTheThresholdExceedsTheTax_givesNothing() {
        // Given — tax at ₹13,00,000 = 60,000 + 15% of 1,00,000 = 75,000; the excess 1,00,000 is larger
        // When
        ReliefResult result = new ExcessOverThreshold(Money.of("1200000")).apply(context("1300000", "75000"));

        // Then
        assertThat(result.amount()).isEqualTo(Money.ZERO);
    }

    @Test
    void excessOverThreshold_whenIncomeIsNotAboveTheThreshold_givesNothing() {
        // When — s.156(2)(b) opens "the total income exceeds twelve lakh rupees"
        ReliefResult result = new ExcessOverThreshold(Money.of("1200000")).apply(context("1200000", "60000"));

        // Then
        assertThat(result.amount()).isEqualTo(Money.ZERO);
    }

    @Test
    void thresholdLadderCeiling_whenIncomeIsJustAboveAThreshold_givesBackTheExcessOverUPlusV() {
        // Given — constructed inputs (surcharge ladders are W-054's): U = ₹10,80,000, the new-regime tax at
        // ₹50L; tax + surcharge at ₹50,00,100 = (10,80,000 + 30% of 100) × 1.10 = 11,88,033;
        // ceiling = U + V = 10,80,000 + 100 = 10,80,100; relief = 11,88,033 − 10,80,100 = 1,07,933
        ReliefShape relief = new ThresholdLadderCeiling(List.of(Money.of("5000000"), Money.of("10000000")));
        UnrelievedTax atThreshold = income -> {
            assertThat(income).isEqualTo(Money.of("5000000"));
            return Money.of("1080000");
        };

        // When
        ReliefResult result = relief.apply(
                new ReliefContext(Money.of("5000100"), Money.of("1188033"), Optional.of(atThreshold)));

        // Then
        assertThat(result.amount()).isEqualTo(Money.of("107933"));
    }

    @Test
    void thresholdLadderCeiling_whenIncomeIsBelowEveryThreshold_givesNothing() {
        // When
        ReliefResult result = new ThresholdLadderCeiling(List.of(Money.of("5000000")))
                .apply(new ReliefContext(Money.of("4000000"), Money.of("780000"),
                        Optional.of(income -> Money.ZERO)));

        // Then
        assertThat(result.amount()).isEqualTo(Money.ZERO);
    }

    @Test
    void thresholdLadderCeiling_whenGivenNoWayToComputeU_isRefusedRatherThanZeroed() {
        // When / Then
        assertThatThrownBy(() -> new ThresholdLadderCeiling(List.of(Money.of("5000000")))
                        .apply(context("5000100", "1188033")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("UnrelievedTax");
    }

    @Test
    void noRelief_whenApplied_givesZeroAndSaysWhy() {
        // When
        ReliefResult result = new NoRelief().apply(context("1000000", "112500"));

        // Then
        assertThat(result.amount()).isEqualTo(Money.ZERO);
        assertThat(result.reason()).isNotBlank();
    }

    @Test
    void reliefResult_whenTheAmountIsNegative_isRefused() {
        // When / Then — an amount to take off is never negative
        assertThatThrownBy(() -> new ReliefResult(Money.of("-1"), "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
