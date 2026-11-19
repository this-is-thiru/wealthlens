package com.thiru.wealthlens.taxengine.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** What retention is measured by (W-137, D-447 C6a): the longest carry-forward a year's pack allows. */
class TaxCalculatorCarryForwardTest {

    @Test
    void longestCarryForward_whenTheShippedYears_isEight() {
        // Given
        TaxCalculator calculator = new TaxCalculator(ShippedRulePacks.compile("rulepacks",
                Set.of(TaxYearId.of(2025), TaxYearId.of(2026))));

        // When / Then — Income-tax Act, 2025 ss.110(2), 111(2) and the 1961 Act's ss.71B, 74: "more than eight tax years"
        assertThat(calculator.longestCarryForward(TaxYearId.of(2025))).hasValue(8);
        assertThat(calculator.longestCarryForward(TaxYearId.of(2026))).hasValue(8);
    }

    @Test
    void longestCarryForward_whenTheApplicationShipsNoPackForTheYear_isEmpty() {
        // Given
        TaxCalculator calculator = new TaxCalculator(ShippedRulePacks.compile("rulepacks", Set.of(TaxYearId.of(2026))));

        // When / Then
        assertThat(calculator.longestCarryForward(TaxYearId.of(2019))).isEmpty();
    }
}
