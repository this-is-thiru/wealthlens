package com.thiru.wealthlens.taxplanning.engine;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HraExemptionCalculatorTest {

    private HraExemptionCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new HraExemptionCalculator();
    }

    // ---- Test 1: metro_highRent_cap2Binding ----
    // Metro: 50% of basic is binding when rent is high enough
    // cap2 = 50% of basic = 2,50,000
    // cap3 = rent - 10% of basic = 2,00,000 - 50,000 = 1,50,000
    // Actual HRA = 2,40,000
    // min(2,40,000, 2,50,000, 1,50,000) = 1,50,000 (cap3 binding)
    // Wait, test name says cap2 (50% of basic) is binding. Let me re-read...
    // Actually cap3 = rent - 10% of basic. With high rent (2L), cap3 = 1.5L and actual HRA = 2.4L.
    // min(2.4L, 2.5L, 1.5L) = 1.5L → cap3 (rent - 10% basic) is binding, not cap2.
    // The test name says "cap2" but the scenario I described has cap3 binding.
    // Let me use a scenario where 50% of basic is truly the minimum.
    // For cap2 (50% basic) to be binding: cap1 = actual HRA, cap2 = 50% basic, cap3 = rent - 10% basic
    // We need: 50% basic < actual HRA AND 50% basic < rent - 10% basic
    // For metro, basic = 5L, actual HRA = 1.8L, rent = 1.2L
    // cap1 = 1.8L, cap2 = 2.5L, cap3 = 1.2L - 0.5L = 0.7L
    // min(1.8L, 2.5L, 0.7L) = 0.7L → cap3 binding again
    // For cap2 (50% basic) to be binding:
    // actualHRA > 50%*basic AND rent - 10%*basic > 50%*basic
    // → rent > 60%*basic → rent > 60% of 5L = 3L
    // With rent = 3L, actualHRA = 2.5L, basic = 5L:
    // cap1 = 2.5L, cap2 = 2.5L, cap3 = 3L - 0.5L = 2.5L
    // min(2.5L, 2.5L, 2.5L) = 2.5L → actual HRA is binding
    // Hmm this is tricky. Let me re-think.
    // Actually the 3-way minimum is: min(actual HRA, 50%/40% of basic, rent - 10% of basic)
    // For cap2 (50% basic) to be binding as the minimum:
    // 50% basic ≤ actual HRA AND 50% basic ≤ rent - 10% basic
    // AND 50% basic is the smallest
    // So we need: actualHRA ≥ 50%*basic AND rent ≥ 60%*basic
    // AND: 50%*basic ≤ rent - 10%*basic → rent ≥ 60%*basic
    // For basic = 5L → rent ≥ 3L. But if rent = 3.2L, actual HRA = 2.7L:
    // cap1 = 2.7L, cap2 = 2.5L, cap3 = 3.2L - 0.5L = 2.7L
    // min = 2.5L → cap2 (50% basic) is binding. Yes!
    @Test
    void metro_highRent_cap2Binding() {
        // Metro, basic = 5L, actual HRA = 2.7L, rent = 3.2L
        // cap1 (actual HRA) = 2,70,000
        // cap2 (50% basic) = 2,50,000  ← binding
        // cap3 (rent - 10% basic) = 3,20,000 - 50,000 = 2,70,000
        // min = 2,50,000

        HraExemptionCalculator.HraResult result = calculator.computeHraExemption(
                2_70_000L,   // actualHraReceivedAnnual
                5_00_000L,   // basicSalaryAnnual
                3_20_000L,   // rentPaidAnnual
                true,        // isMetroCity
                true         // isPayingRent
        );

        assertEquals(2_50_000L, result.getExemptionAmount());
    }

    // ---- Test 2: metro_lowRent_cap3Binding ----
    // Metro: low rent → cap3 (rent - 10% basic) is the minimum
    // basic = 5L, actual HRA = 2.4L, rent = 1.8L
    // cap1 = 2.4L, cap2 = 2.5L, cap3 = 1.8L - 0.5L = 1.3L ← binding
    @Test
    void metro_lowRent_cap3Binding() {
        HraExemptionCalculator.HraResult result = calculator.computeHraExemption(
                2_40_000L,   // actualHraReceivedAnnual
                5_00_000L,   // basicSalaryAnnual
                1_80_000L,   // rentPaidAnnual
                true,        // isMetroCity
                true         // isPayingRent
        );

        assertEquals(1_30_000L, result.getExemptionAmount());
    }

    // ---- Test 3: nonMetro_mediumRent_cap1Binding ----
    // Non-metro: 40% cap, actual HRA is the minimum when rent is moderate
    // basic = 5L, actual HRA = 1.6L, rent = 1.2L (annual)
    // cap1 = 1.6L, cap2 (40% basic) = 2L, cap3 = 1.2L - 0.5L = 0.7L
    // min = 0.7L → cap3 binding again
    // For actual HRA to be binding: actualHRA ≤ 40%*basic AND actualHRA ≤ rent - 10%*basic
    // Actually let me try: basic = 6L, actualHRA = 1.2L, rent = 1.5L
    // cap1 = 1.2L, cap2 (40% basic) = 2.4L, cap3 = 1.5L - 0.6L = 0.9L
    // min = 0.9L → cap3 binding again
    // This calculator consistently gives cap3 as the minimum when rent is low/moderate.
    // Let me try: actual HRA is binding = min(all three) = actual HRA
    // This requires: actualHRA ≤ 40%*basic AND actualHRA ≤ rent - 10%*basic
    // With basic = 5L, actualHRA = 0.6L, rent = 1.2L
    // cap1 = 0.6L, cap2 = 2L, cap3 = 1.2L - 0.5L = 0.7L
    // min = 0.6L → actual HRA is binding!
    @Test
    void nonMetro_mediumRent_cap1Binding() {
        // Non-metro, actual HRA is low enough to be the minimum
        HraExemptionCalculator.HraResult result = calculator.computeHraExemption(
                60_000L,     // actualHraReceivedAnnual (low HRA)
                5_00_000L,   // basicSalaryAnnual
                1_20_000L,   // rentPaidAnnual
                false,       // isMetroCity
                true         // isPayingRent
        );

        // cap1 = 60,000 (actual HRA, lowest)
        // cap2 (40% basic) = 2,00,000
        // cap3 (rent - 10% basic) = 1,20,000 - 50,000 = 70,000
        // min = 60,000 → actual HRA is binding
        assertEquals(60_000L, result.getExemptionAmount());
    }

    // ---- Test 4: notPayingRent_returnsZero ----

    @Test
    void notPayingRent_returnsZero() {
        HraExemptionCalculator.HraResult result = calculator.computeHraExemption(
                2_40_000L,
                5_00_000L,
                1_80_000L,
                true,
                false  // NOT paying rent
        );

        assertEquals(0L, result.getExemptionAmount());
        assertTrue(result.getWarnings().stream()
                .anyMatch(w -> w.contains("not paying rent")));
    }

    // ---- Test 5: annualRentAbove1L_landlordPanWarning ----

    @Test
    void annualRentAbove1L_landlordPanWarning() {
        // Rent > ₹1,00,000 with no landlord PAN → warning generated
        HraExemptionCalculator.HraResult result = calculator.computeHraExemption(
                2_40_000L,
                5_00_000L,
                1_50_000L,   // rent > 1L
                true,
                true
        );

        assertTrue(result.getWarnings().stream()
                .anyMatch(w -> w.contains("landlord PAN")));
        assertTrue(result.getExemptionAmount() > 0);
    }
}
