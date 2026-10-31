package com.thiru.wealthlens.taxengine.rules.temporal.proration;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

/** Calendar months the window touches: a part month counts as one, as "per calendar month" reads. */
public record CalendarMonths() implements ProrationBasis {

    @Override
    public int units(TaxYearId year, DateRange window) {
        return ProrationBasis.withinYear(year, window)
                .map(part -> Math.toIntExact(ChronoUnit.MONTHS.between(
                        YearMonth.from(part.from()), YearMonth.from(part.to().orElseThrow())) + 1))
                .orElse(0);
    }

    @Override
    public int unitsInYear(TaxYearId year) {
        return 12;
    }
}
