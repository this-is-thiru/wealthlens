package com.thiru.wealthlens.taxengine.rules.temporal.proration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * The proration bases (D-097). Every count is a calendar fact counted by hand in the comment beside it; none is
 * law. TY 2025-26 runs 1 Apr 2025 – 31 Mar 2026 (365 days); TY 2027-28 contains 29 Feb 2028 (366 days).
 */
class ProrationBasisTest {

    private static final TaxYearId TY_2025 = TaxYearId.of(2025);
    private static final TaxYearId TY_2027 = TaxYearId.of(2027);
    private static final ProrationBasis DAYS = new Days();
    private static final ProrationBasis CALENDAR_MONTHS = new CalendarMonths();
    private static final ProrationBasis COMPLETED_MONTHS = new CompletedMonths();

    private static DateRange between(String from, String to) {
        return DateRange.between(LocalDate.parse(from), LocalDate.parse(to));
    }

    @Test
    void units_whenTheWindowIsTheWholeYear_isTheYearInEachUnit() {
        // Given
        DateRange year = between("2025-04-01", "2026-03-31");

        // Then — 365 days; April to March is 12 months either way
        assertThat(DAYS.units(TY_2025, year)).isEqualTo(365);
        assertThat(CALENDAR_MONTHS.units(TY_2025, year)).isEqualTo(12);
        assertThat(COMPLETED_MONTHS.units(TY_2025, year)).isEqualTo(12);
    }

    @Test
    void units_whenTheYearHasTwentyNinthFebruary_countsItsThreeHundredAndSixtySixDays() {
        // Then — 1 Apr 2027 – 31 Mar 2028, and 2028 is a leap year
        assertThat(DAYS.units(TY_2027, between("2027-04-01", "2028-03-31"))).isEqualTo(366);
    }

    @Test
    void units_whenTheWindowIsJulyToDecember_countsEachWholeMonth() {
        // Given
        DateRange window = between("2025-07-01", "2025-12-31");

        // Then — 31 + 31 + 30 + 31 + 30 + 31 = 184 days; six months, all whole
        assertThat(DAYS.units(TY_2025, window)).isEqualTo(184);
        assertThat(CALENDAR_MONTHS.units(TY_2025, window)).isEqualTo(6);
        assertThat(COMPLETED_MONTHS.units(TY_2025, window)).isEqualTo(6);
    }

    @Test
    void units_whenTheWindowStartsBeforeTheYear_countsOnlyTheYearsPart() {
        // Given — 1 Jan – 30 Apr 2025; only 1–30 Apr is in TY 2025-26
        DateRange window = between("2025-01-01", "2025-04-30");

        // Then — 30 days; April, whole
        assertThat(DAYS.units(TY_2025, window)).isEqualTo(30);
        assertThat(CALENDAR_MONTHS.units(TY_2025, window)).isEqualTo(1);
        assertThat(COMPLETED_MONTHS.units(TY_2025, window)).isEqualTo(1);
    }

    @Test
    void units_whenTheWindowRunsPastTheYear_countsOnlyTheYearsPart() {
        // Given — 1 Mar – 31 May 2026; only 1–31 Mar is in TY 2025-26
        DateRange window = between("2026-03-01", "2026-05-31");

        // Then — 31 days; March, whole
        assertThat(DAYS.units(TY_2025, window)).isEqualTo(31);
        assertThat(CALENDAR_MONTHS.units(TY_2025, window)).isEqualTo(1);
        assertThat(COMPLETED_MONTHS.units(TY_2025, window)).isEqualTo(1);
    }

    @Test
    void units_whenTheWindowIsOutsideTheYear_isNil() {
        // Given — TY 2024-25 exactly
        DateRange window = between("2024-04-01", "2025-03-31");

        // Then
        assertThat(DAYS.units(TY_2025, window)).isZero();
        assertThat(CALENDAR_MONTHS.units(TY_2025, window)).isZero();
        assertThat(COMPLETED_MONTHS.units(TY_2025, window)).isZero();
    }

    @Test
    void units_whenTheWindowHasNoEnd_runsToTheYearsEnd() {
        // Given — from 1 Jan 2026
        DateRange window = DateRange.fromOnwards(LocalDate.parse("2026-01-01"));

        // Then — 31 + 28 + 31 = 90 days (2026 is not a leap year); January to March
        assertThat(DAYS.units(TY_2025, window)).isEqualTo(90);
        assertThat(CALENDAR_MONTHS.units(TY_2025, window)).isEqualTo(3);
        assertThat(COMPLETED_MONTHS.units(TY_2025, window)).isEqualTo(3);
    }

    @Test
    void units_whenAChildIsInHostelForTwentyDays_isOneCalendarMonthAndNoCompletedMonth() {
        // Given — DR 13 B2-20: 5–24 Jan 2026
        DateRange window = between("2026-01-05", "2026-01-24");

        // Then — 20 days; one month or none, never 0.65
        assertThat(DAYS.units(TY_2025, window)).isEqualTo(20);
        assertThat(CALENDAR_MONTHS.units(TY_2025, window)).isEqualTo(1);
        assertThat(COMPLETED_MONTHS.units(TY_2025, window)).isZero();
    }

    @Test
    void units_whenTheWindowSpansPartMonthsAtBothEnds_countsPartMonthsOnlyAsCalendarMonths() {
        // Given — 15 Apr – 14 Jun 2025
        DateRange window = between("2025-04-15", "2025-06-14");

        // Then — 16 + 31 + 14 = 61 days; April, May and June are touched; only May is whole
        assertThat(DAYS.units(TY_2025, window)).isEqualTo(61);
        assertThat(CALENDAR_MONTHS.units(TY_2025, window)).isEqualTo(3);
        assertThat(COMPLETED_MONTHS.units(TY_2025, window)).isEqualTo(1);
    }

    @Test
    void apportion_whenHalfAYearByDays_dividesOnceAndRoundsToPaise() {
        // When — 1,20,000 × 184 / 365 = 60,493.150684…
        Money share = DAYS.apportion(Money.of("120000"), TY_2025, between("2025-07-01", "2025-12-31"));

        // Then
        assertThat(share).isEqualTo(Money.of("60493.15"));
    }

    @Test
    void apportion_whenTheWholeYear_isTheYearsAmountExactly() {
        // Then — 365 / 365 and 12 / 12 leave nothing to round
        assertThat(DAYS.apportion(Money.of("120000"), TY_2025, between("2025-04-01", "2026-03-31")))
                .isEqualTo(Money.of("120000"));
        assertThat(CALENDAR_MONTHS.apportion(Money.of("120000"), TY_2025, between("2025-04-01", "2026-03-31")))
                .isEqualTo(Money.of("120000"));
    }

    @Test
    void apportion_whenTheYearHasTwentyNinthFebruary_dividesByThreeHundredAndSixtySix() {
        // When — 3,66,000 for one day of TY 2027-28: 3,66,000 × 1 / 366 = 1,000 exactly (by 365 it would be 1,002.74)
        Money oneDay = DAYS.apportion(Money.of("366000"), TY_2027, between("2027-07-01", "2027-07-01"));

        // Then
        assertThat(DAYS.unitsInYear(TY_2027)).isEqualTo(366);
        assertThat(oneDay).isEqualTo(Money.of("1000"));
    }

    @Test
    void apportion_whenThreeCalendarMonths_isAQuarterOfTheYear() {
        // When — 1,20,000 × 3 / 12 = 30,000 (15 Apr – 14 Jun touches three months)
        Money share = CALENDAR_MONTHS.apportion(Money.of("120000"), TY_2025, between("2025-04-15", "2025-06-14"));

        // Then
        assertThat(share).isEqualTo(Money.of("30000"));
    }

    @Test
    void units_whenGivenNoWindow_isRefused() {
        assertThatThrownBy(() -> DAYS.units(TY_2025, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
