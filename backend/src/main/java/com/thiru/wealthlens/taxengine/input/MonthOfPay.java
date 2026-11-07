package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.YearMonth;
import java.util.Map;

/**
 * What one calendar month paid, by pay component (D-106): the unit a ten-month average salary is measured from.
 *
 * <p>By component rather than as one figure, so that which lines count as "salary" stays the salary base's decision
 * (D-092) instead of the taxpayer's.
 *
 * @param month the calendar month
 * @param amounts what each component paid in it
 */
public record MonthOfPay(YearMonth month, Map<ComponentCode, Money> amounts) {

    public MonthOfPay {
        if (month == null || amounts == null) {
            throw new IllegalArgumentException("A month of pay needs its month and amounts; pass an empty map where"
                    + " nothing was paid");
        }
        if (amounts.values().stream().anyMatch(amount -> amount.isLessThan(Money.ZERO))) {
            throw new IllegalArgumentException(month + ": a month's pay may not be negative");
        }
        amounts = Map.copyOf(amounts);
    }
}
