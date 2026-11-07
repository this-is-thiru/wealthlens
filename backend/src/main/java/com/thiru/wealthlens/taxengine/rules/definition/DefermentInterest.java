package com.thiru.wealthlens.taxengine.rules.definition;

import com.thiru.wealthlens.taxengine.rules.money.Rate;
import java.time.MonthDay;
import java.util.List;
import java.util.Optional;

/**
 * Interest for paying advance tax late, instalment by instalment (2025 s.425(1) Table, (2); 1961 s.234C(1)(a) and its
 * proviso; W-112): at each date, the shortfall from the stated share of the tax due on the returned income bears the stated
 * interest, unless what was paid reaches the date's lower share. s.425 states its own dates and shares, so they are held here
 * as the section states them, apart from s.408's schedule.
 *
 * @param steps in date order through the financial year
 */
public record DefermentInterest(List<Step> steps) implements RuleDefinition {

    public DefermentInterest {
        if (steps == null || steps.isEmpty()) {
            throw new IllegalArgumentException("Deferment interest needs at least one date");
        }
        steps = List.copyOf(steps);
    }

    /**
     * One date.
     *
     * @param dueOn the day of the year paid amounts are counted to
     * @param share the share of the tax due on the returned income due by then
     * @param interest the interest on the shortfall, once, not by month
     * @param noInterestFrom the share which, if paid by then, means no interest on that date; {@code Optional.empty()} where
     *     the date has none
     */
    public record Step(MonthDay dueOn, Rate share, Rate interest, Optional<Rate> noInterestFrom) {

        public Step {
            if (dueOn == null || share == null || interest == null || noInterestFrom == null) {
                throw new IllegalArgumentException("A deferment date needs its day, share, interest and lower share or"
                        + " Optional.empty()");
            }
        }
    }
}
