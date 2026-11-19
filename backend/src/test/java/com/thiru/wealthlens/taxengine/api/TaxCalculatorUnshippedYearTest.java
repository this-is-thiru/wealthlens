package com.thiru.wealthlens.taxengine.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.CapitalGainsInput;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.rules.generated.ComponentCodes;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import com.thiru.wealthlens.taxengine.test.Fixtures;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

/**
 * A year the application ships no pack for is the caller's to correct, so each entry that prices a year refuses it as
 * an {@link IllegalArgumentException} naming the years shipped, never as a pack defect (W-263). The calculator here
 * ships TY 2025-26 only and the inputs are TY 2026-27's, so the message is known without a run.
 */
class TaxCalculatorUnshippedYearTest {

    private static final String REFUSAL = "No rule pack ships for 2026-27; the application ships [2025-26]";
    private static final TaxCalculator CALCULATOR = new TaxCalculator(ShippedRulePacks.compile("rulepacks",
            Set.of(TaxYearId.of(2025))));
    private static final EngineInputs TY_2026_27_INPUTS = Fixtures.rentingInMumbai("1600000", "800000", "960000", List.of());

    private static void assertRefused(ThrowingCallable call) {
        assertThatThrownBy(call).isExactlyInstanceOf(IllegalArgumentException.class).hasMessage(REFUSAL);
    }

    @Test
    void compute_whenNoPackShipsForTheYear_refusesNamingTheYearsShipped() {
        assertThat(TY_2026_27_INPUTS.taxYear()).isEqualTo(TaxYearId.of(2026));
        assertRefused(() -> CALCULATOR.compute(TY_2026_27_INPUTS));
    }

    @Test
    void compareRegimes_whenNoPackShipsForTheYear_refusesNamingTheYearsShipped() {
        assertRefused(() -> CALCULATOR.compareRegimes(TY_2026_27_INPUTS));
    }

    @Test
    void optimise_whenNoPackShipsForTheYear_refusesNamingTheYearsShipped() {
        // Given
        AllocationSpace space = new AllocationSpace(TY_2026_27_INPUTS,
                new AllocationSpace.Moves(Set.of(ComponentCodes.HRA), Set.of(), Set.of()), ComponentCodes.SPECIAL_ALLOWANCE,
                Money.of("0"), 6, ProvidentFundTerms.FOLLOW_BASIC);

        // When / Then
        assertRefused(() -> CALCULATOR.optimise(space));
    }

    @Test
    void termIfSold_whenNoPackShipsForTheYear_refusesNamingTheYearsShipped() {
        // Given
        CapitalGainsInput.BucketFacts facts = new CapitalGainsInput.BucketFacts("EQUITY", "UNKNOWN", "UNKNOWN", "PURCHASE",
                Optional.empty(), LocalDate.of(2025, 1, 10), LocalDate.of(2026, 6, 1), CapitalGainsInput.SideVenue.UNRECORDED,
                CapitalGainsInput.SideVenue.UNRECORDED);

        // When / Then
        assertRefused(() -> CALCULATOR.termIfSold(TY_2026_27_INPUTS, facts));
    }

    @Test
    void defaultRegime_whenNoPackShipsForTheYear_refusesNamingTheYearsShipped() {
        assertRefused(() -> CALCULATOR.defaultRegime(TaxYearId.of(2026)));
    }

    @Test
    void defaultRegime_whenThePackShips_isUnchanged() {
        assertThat(CALCULATOR.defaultRegime(TaxYearId.of(2025)).value()).isEqualTo("NEW_TAX_REGIME");
    }
}
