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
 * <p>The relief (2025 s.425(4); 1961 s.234C(1) first proviso; W-112a): no interest on the part of a shortfall that comes
 * from the incomes the section names, where the tax on them was paid in a remaining instalment or by its paid-by day. Which
 * incomes, and that day, are the year's data, so a list the law changes is a new version, not new code.
 *
 * @param steps in date order through the financial year
 * @param relievedIncomes the incomes whose shortfall is relieved, as the section names them
 * @param relievedIfPaidBy the day of the year by which their tax must be paid for the relief to hold
 */
public record DefermentInterest(List<Step> steps, List<RelievedIncome> relievedIncomes, MonthDay relievedIfPaidBy)
        implements RuleDefinition {

    public DefermentInterest {
        if (steps == null || steps.isEmpty()) {
            throw new IllegalArgumentException("Deferment interest needs at least one date");
        }
        if (relievedIncomes == null || relievedIfPaidBy == null) {
            throw new IllegalArgumentException("Deferment interest needs its relieved incomes, an empty list for none, and"
                    + " the day their tax must be paid by");
        }
        steps = List.copyOf(steps);
        relievedIncomes = List.copyOf(relievedIncomes);
    }

    /** The incomes s.425(4)(a) and s.234C's first proviso name. */
    public enum RelievedIncome {

        /** Capital gains (2025 s.425(4)(a)(i); 1961 proviso (a)). */
        CAPITAL_GAINS,

        /** Winnings from lotteries, races, games and betting (2025 s.2(49)(n), s.425(4)(a)(ii); 1961 s.2(24)(ix)). */
        WINNINGS,

        /** Business or professional income arising for the first time (2025 s.425(4)(a)(iii); 1961 proviso (c)). */
        FIRST_TIME_BUSINESS_INCOME,

        /** Dividend income, other than a s.2(40)(e) / s.2(22)(e) dividend (2025 s.425(4)(a)(iv), (6); 1961 (d)). */
        DIVIDEND
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
