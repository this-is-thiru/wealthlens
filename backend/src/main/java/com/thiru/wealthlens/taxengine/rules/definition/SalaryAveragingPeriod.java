package com.thiru.wealthlens.taxengine.rules.definition;

/**
 * The period a salary is averaged over: the {@code months} calendar months immediately preceding the month of the
 * event (S2 s.19(1) Table Sl. 6 "A = average salary for ten months immediately preceding the month when any such event
 * occurs"; 1961 s.10(10)(iii); W-152, D-317).
 *
 * <p>The period is the law's, so it is a row's, and a change in it is a new version; which pay counts as salary is
 * {@code SAL-SALARY-FOR-GRATUITY}'s.
 *
 * @param months how many months are averaged; positive
 */
public record SalaryAveragingPeriod(int months) implements RuleDefinition {

    public SalaryAveragingPeriod {
        if (months <= 0) {
            throw new IllegalArgumentException("A salary-averaging row must name a positive number of months, not "
                    + months);
        }
    }
}
