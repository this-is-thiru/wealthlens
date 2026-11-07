package com.thiru.wealthlens.taxengine.rules.temporal.proration;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

/**
 * Calendar months lying wholly within the window: a part month counts as none (D-097). A child in hostel for
 * twenty days is no month, never a fraction of one ({@code DR 13} B2-20); counting whole calendar months keeps a
 * month the same unit as {@link CalendarMonths}'.
 */
public record CompletedMonths() implements ProrationBasis {

    @Override
    public int units(TaxYearId year, DateRange window) {
        return ProrationBasis.withinYear(year, window).map(part -> {
            YearMonth first = part.from().getDayOfMonth() == 1
                    ? YearMonth.from(part.from())
                    : YearMonth.from(part.from()).plusMonths(1);
            YearMonth lastTouched = YearMonth.from(part.to().orElseThrow());
            YearMonth last = part.to().orElseThrow().equals(lastTouched.atEndOfMonth())
                    ? lastTouched
                    : lastTouched.minusMonths(1);
            return Math.toIntExact(Math.max(0, ChronoUnit.MONTHS.between(first, last) + 1));
        }).orElse(0);
    }

    @Override
    public int unitsInYear(TaxYearId year) {
        return 12;
    }
}
