package com.thiru.wealthlens.taxengine.rules.temporal.proration;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;
import java.time.temporal.ChronoUnit;

/**
 * Days, both ends inclusive, over the year's actual days — 366 where the year holds 29 February. For a period
 * the rule measures by occupation, such as HRA's relevant period ({@code SAL-HRA-003}).
 */
public record Days() implements ProrationBasis {

    @Override
    public int units(TaxYearId year, DateRange window) {
        return ProrationBasis.withinYear(year, window)
                .map(part -> Math.toIntExact(ChronoUnit.DAYS.between(part.from(), part.to().orElseThrow()) + 1))
                .orElse(0);
    }

    @Override
    public int unitsInYear(TaxYearId year) {
        return Math.toIntExact(ChronoUnit.DAYS.between(year.startsOn(), year.endsOn()) + 1);
    }
}
