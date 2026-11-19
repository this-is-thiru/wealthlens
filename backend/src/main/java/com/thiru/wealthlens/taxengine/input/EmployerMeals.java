package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.money.Money;

/**
 * Meals of one value an employer provided in the year (S4 r.15(5) Table IV Sl. 3; Rules 1962 r.3(7)(iii); D-117).
 *
 * <p>The limit is "per meal", so meals of different values are separate entries: one meal under the limit does not
 * make room for another above it. A meal's value is the employer's cost less what the employee paid.
 *
 * @param employment which entered employment provides them, by position
 * @param setting where and how they are provided
 * @param meals how many
 * @param costPerMeal the employer's expenditure on each
 * @param paidPerMeal what the employee paid, or had recovered, for each
 */
public record EmployerMeals(int employment, MealSetting setting, int meals, Money costPerMeal, Money paidPerMeal) {

    public EmployerMeals {
        if (setting == null || costPerMeal == null || paidPerMeal == null) {
            throw new IllegalArgumentException("Meals need their setting, cost and payment; enter zero for none paid");
        }
        if (employment < 0 || meals < 0) {
            throw new IllegalArgumentException("An employment position and a number of meals may not be negative");
        }
        if (paidPerMeal.isLessThan(Money.ZERO) || paidPerMeal.isGreaterThan(costPerMeal)) {
            throw new IllegalArgumentException("The employee's payment per meal must lie between nil and the cost ("
                    + costPerMeal + "), but was " + paidPerMeal);
        }
    }

    /** The value of each meal to the employee: the cost less what was paid. */
    public Money valuePerMeal() {
        return costPerMeal.minus(paidPerMeal);
    }
}
