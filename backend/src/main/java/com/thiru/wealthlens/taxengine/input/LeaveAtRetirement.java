package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;

/**
 * What leave encashment's limits read, as the employer computed them (S2 s.19(1) Table Sl. 14; 1961
 * s.10(10AA)(ii); D-106).
 *
 * <p>Both figures are the employer's. The leave at credit is capped at "thirty days for every year of actual
 * service", and neither Act says how many days make a month, so converting leave days into money would be a rule
 * the engine invents. And the average is over the ten months "immediately preceding his retirement" — a period
 * ending on the day, which calendar months do not give.
 *
 * <p>The leave figure means different things in the two years. For TY 2026-27 it is "the cash equivalent of the
 * leave salary" for the leave at credit (S2 s.19(1) Table Sl. 14(a)) — what the employer pays for it. For TY 2025-26
 * the 1961 clause has no such limb: it values the leave at credit "on the basis of the average salary" (s.10(10AA)(ii)),
 * so the figure is that leave valued at the ten-month average, not at the rate the employer paid. And "salary" is each
 * year's own: Sch. IV Pt A r.2(h) for TY 2025-26, s.19(2)(b) for TY 2026-27.
 *
 * @param retiredOn the day of retirement, whether on superannuation or otherwise
 * @param leaveSalaryAtCredit the earned leave at credit, entitlement capped at thirty days for every year of actual
 *     service, as its cash equivalent (TY 2026-27) or valued at the ten-month average salary (TY 2025-26)
 * @param averageMonthlySalary the average monthly salary for the ten months immediately preceding retirement
 */
public record LeaveAtRetirement(LocalDate retiredOn, Money leaveSalaryAtCredit, Money averageMonthlySalary) {

    public LeaveAtRetirement {
        if (retiredOn == null || leaveSalaryAtCredit == null || averageMonthlySalary == null) {
            throw new IllegalArgumentException("Leave at retirement needs the day and both of the employer's figures");
        }
        if (leaveSalaryAtCredit.isLessThan(Money.ZERO) || averageMonthlySalary.isLessThan(Money.ZERO)) {
            throw new IllegalArgumentException("The employer's leave figures may not be negative");
        }
    }
}
