package com.thiru.wealthlens.taxengine.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.input.CapitalGainsInput;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import com.thiru.wealthlens.taxengine.test.Fixtures;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * A held lot's term and the day it turns long-term, through {@link TaxCalculator#termIfSold} (W-262). The lot is a
 * share whose sale would be on NSE, so TY 2026-27's {@code CG-TERM-LISTED-SECURITY} reads it: twelve months, long-term
 * only when held for more than that (D-303). Bought 1 June 2025, the twelve months end on 1 June 2026.
 */
class TaxCalculatorHeldTermTest {

    private static final TaxCalculator CALCULATOR = new TaxCalculator(ShippedRulePacks.compile("rulepacks",
            Set.of(TaxYearId.of(2026))));
    private static final EngineInputs INPUTS = Fixtures.rentingInMumbai("1600000", "800000", "960000", List.of());

    private static TaxCalculator.HeldTerm ifSoldOn(LocalDate soldOn) {
        return CALCULATOR.termIfSold(INPUTS, new CapitalGainsInput.BucketFacts("EQUITY", "UNKNOWN", "UNKNOWN", "PURCHASE",
                Optional.empty(), LocalDate.of(2025, 6, 1), soldOn, CapitalGainsInput.SideVenue.UNRECORDED,
                new CapitalGainsInput.SideVenue(Optional.of("NSE"), Optional.empty())));
    }

    @Test
    void termIfSold_whenSoldOnTheLastDayOfThePeriod_isShortTermAndNamesTheNextDay() {
        // When
        TaxCalculator.HeldTerm term = ifSoldOn(LocalDate.of(2026, 6, 1));

        // Then
        assertThat(term.term()).isEqualTo("SHORT_TERM");
        assertThat(term.row()).isEqualTo("CG-TERM-LISTED-SECURITY");
        assertThat(term.months()).isEqualTo(12);
        assertThat(term.longTermFrom()).isEqualTo(LocalDate.of(2026, 6, 2));
    }

    @Test
    void termIfSold_whenSoldOnTheDayItNames_isLongTerm() {
        // When
        TaxCalculator.HeldTerm term = ifSoldOn(LocalDate.of(2026, 6, 2));

        // Then
        assertThat(term.term()).isEqualTo("LONG_TERM");
        assertThat(term.longTermFrom()).isEqualTo(LocalDate.of(2026, 6, 2));
    }
}
