package com.thiru.wealthlens.taxengine.rules.temporal;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import java.time.LocalDate;
import java.util.Optional;

/**
 * A closed or open-ended interval of dates, both ends inclusive.
 *
 * <p>Used wherever something is in force for a period: a regime's entry window and its in-force
 * window, a component's lifetime, an employment. The two regime windows are deliberately separate
 * intervals — a regime can be closed to new entrants while still applying to those already in it,
 * which is the position sections 201 and 204 are in today.
 *
 * @param from inclusive
 * @param to inclusive; empty means open-ended
 */
public record DateRange(LocalDate from, Optional<LocalDate> to) {

    public DateRange {
        if (from == null) {
            throw new IllegalArgumentException("DateRange.from may not be null");
        }
        to = to == null ? Optional.empty() : to;
        if (to.isPresent() && to.get().isBefore(from)) {
            throw new IllegalArgumentException(
                    "DateRange ends before it begins: " + from + " to " + to.get());
        }
    }

    public static DateRange between(LocalDate from, LocalDate to) {
        return new DateRange(from, Optional.of(to));
    }

    public static DateRange fromOnwards(LocalDate from) {
        return new DateRange(from, Optional.empty());
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(from) && to.map(end -> !date.isAfter(end)).orElse(true);
    }

    /** Whether this range covers the whole of a tax year, both boundaries included. */
    public boolean coversTaxYear(TaxYearId taxYear) {
        return contains(taxYear.startsOn()) && contains(taxYear.endsOn());
    }

    /** Whether this range covers any part of a tax year. */
    public boolean overlapsTaxYear(TaxYearId taxYear) {
        return overlaps(DateRange.between(taxYear.startsOn(), taxYear.endsOn()));
    }

    public boolean overlaps(DateRange other) {
        boolean thisStartsAfterOtherEnds = other.to.map(from::isAfter).orElse(false);
        boolean otherStartsAfterThisEnds = to.map(other.from::isAfter).orElse(false);
        return !thisStartsAfterOtherEnds && !otherStartsAfterThisEnds;
    }

    /** Whether this range lies entirely within another. Regime entry windows must satisfy this. */
    public boolean liesWithin(DateRange outer) {
        if (!outer.contains(from)) {
            return false;
        }
        if (to.isEmpty()) {
            return outer.to.isEmpty();
        }
        return outer.contains(to.get());
    }

    @Override
    public String toString() {
        return from + " to " + to.map(LocalDate::toString).orElse("open-ended");
    }
}
