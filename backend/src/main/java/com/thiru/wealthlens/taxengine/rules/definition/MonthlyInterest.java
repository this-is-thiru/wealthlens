package com.thiru.wealthlens.taxengine.rules.definition;

import com.thiru.wealthlens.taxengine.rules.money.Rate;

/**
 * Simple interest by the month for furnishing the return late (2025 s.423(1); 1961 s.234A(1); W-112).
 *
 * @param perMonth the interest for each month
 * @param statesPartOfMonth whether the section itself says "every month or part of a month": 1961 s.234A does, 2025 s.423
 *     does not, and a part month is counted whole under either only as the reading OQ-39 records, which the trace says
 */
public record MonthlyInterest(Rate perMonth, boolean statesPartOfMonth) implements RuleDefinition {

    public MonthlyInterest {
        if (perMonth == null || perMonth.compareTo(Rate.ZERO) <= 0) {
            throw new IllegalArgumentException("Monthly interest needs a rate above nil, but was " + perMonth);
        }
    }
}
