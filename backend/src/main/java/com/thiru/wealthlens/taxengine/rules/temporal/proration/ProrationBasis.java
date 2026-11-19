package com.thiru.wealthlens.taxengine.rules.temporal.proration;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;
import java.time.LocalDate;
import java.util.Optional;

/**
 * How a rule measures the part of a tax year it applies to — named per rule, so the choice is data and visible
 * ({@code DR 15} C3, D-097).
 *
 * <p>At least eight rules prorate, each differently, and writing the arithmetic at every caller is how the HRA
 * per-period defect survived two passes. So a basis answers the two questions callers have and no caller
 * divides: how many whole units of the window fall in the year — the count a per-month cap multiplies — and what
 * share of a year's amount the window carries, divided once and rounded once in {@link Money}. There is no
 * public fraction: a fraction handed out is a division every caller repeats and rounds its own way.
 */
public sealed interface ProrationBasis permits Days, CalendarMonths, CompletedMonths {

    /** How many whole units of {@code window} fall within {@code year}; nil where they do not meet. */
    int units(TaxYearId year, DateRange window);

    /** The units in the whole of {@code year} — 365 or 366 days, or 12 months. */
    int unitsInYear(TaxYearId year);

    /** The share of a year's amount that {@code window} carries: {@code forTheYear × units ÷ unitsInYear}. */
    default Money apportion(Money forTheYear, TaxYearId year, DateRange window) {
        if (forTheYear == null) {
            throw new IllegalArgumentException("Apportioning needs the year's amount");
        }
        return forTheYear.apportioned(units(year, window), unitsInYear(year));
    }

    /** The part of {@code window} inside {@code year}, both ends inclusive; empty where they do not meet. */
    static Optional<DateRange> withinYear(TaxYearId year, DateRange window) {
        if (year == null || window == null) {
            throw new IllegalArgumentException("Proration needs a tax year and a window");
        }
        LocalDate from = window.from().isAfter(year.startsOn()) ? window.from() : year.startsOn();
        LocalDate to = window.to().filter(end -> end.isBefore(year.endsOn())).orElse(year.endsOn());
        return from.isAfter(to) ? Optional.empty() : Optional.of(DateRange.between(from, to));
    }
}
