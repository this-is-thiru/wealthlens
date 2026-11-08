package com.thiru.wealthlens.taxengine.rules.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.registry.Provenance;
import com.thiru.wealthlens.taxengine.rules.registry.VerificationLevel;
import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Which days of a span a row's liquidity leaves unaccounted for (W-238e1). */
class LiquidityTest {

    private static final Provenance READ = new Provenance(URI.create("https://egazette.gov.in/fixture.pdf"),
            "fixture", LocalDate.of(2026, 11, 8), VerificationLevel.SOURCE_CHECKED, null);
    private static final DateRange YEAR = DateRange.between(LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31));

    private static WithdrawalTerm term(String event, DateRange validity) {
        return new WithdrawalTerm(event, "fixture terms", validity, READ);
    }

    @Test
    void firstUnaccountedDay_whenAnUnstatedPeriodMeetsATerm_findsNone() {
        // Given — unstated to 28-6, the term from 29-6, as the provident fund's rows
        Liquidity liquidity = new Liquidity(
                List.of(term("Full withdrawal", DateRange.fromOnwards(LocalDate.of(2026, 6, 29)))),
                List.of(new UnstatedTerms(DateRange.between(LocalDate.of(2025, 4, 1), LocalDate.of(2026, 6, 28)), "why")));

        // When / Then
        assertThat(liquidity.firstUnaccountedDay(YEAR)).isEmpty();
    }

    @Test
    void firstUnaccountedDay_whenTwoTermsLeaveAGap_namesItsFirstDay() {
        // Given — 1-4 to 30-6, then from 2-7: 1-7 is uncovered
        Liquidity liquidity = new Liquidity(List.of(
                term("Later", DateRange.fromOnwards(LocalDate.of(2026, 7, 2))),
                term("Earlier", DateRange.between(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30)))), List.of());

        // When / Then
        assertThat(liquidity.firstUnaccountedDay(YEAR)).contains(LocalDate.of(2026, 7, 1));
    }

    @Test
    void firstUnaccountedDay_whenTheTermsEndBeforeTheSpan_namesTheDayAfter() {
        // Given
        Liquidity liquidity = new Liquidity(
                List.of(term("Ended", DateRange.between(LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 30)))), List.of());

        // When / Then
        assertThat(liquidity.firstUnaccountedDay(YEAR)).contains(LocalDate.of(2027, 3, 31));
        assertThat(Liquidity.NONE.firstUnaccountedDay(YEAR)).contains(LocalDate.of(2026, 4, 1));
    }

    @Test
    void new_whenATermOverlapsAnUnstatedPeriod_isRefused() {
        // Given
        List<WithdrawalTerm> terms = List.of(term("Full withdrawal", DateRange.fromOnwards(LocalDate.of(2026, 6, 28))));
        List<UnstatedTerms> gaps = List.of(new UnstatedTerms(
                DateRange.between(LocalDate.of(2025, 4, 1), LocalDate.of(2026, 6, 28)), "why"));

        // When / Then
        assertThatThrownBy(() -> new Liquidity(terms, gaps)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlaps a period without stated terms");
    }
}
