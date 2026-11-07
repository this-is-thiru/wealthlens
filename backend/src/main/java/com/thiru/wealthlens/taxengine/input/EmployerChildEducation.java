package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.money.Money;

/**
 * Education for a child of the employee in the employer's own institution, or free elsewhere by reason of the
 * employment (S4 r.15(4) Table III Sl. 3(b)–(c); Rules 1962 r.3(5) and proviso; D-120).
 *
 * <p>One entry per child: the limit is "per child", "per month", and all or nothing (D-120 C3).
 *
 * @param employment which entered employment it comes with, by position
 * @param inEmployersInstitution whether the institution is the employer's own (Sl. 3(b)) rather than another one free
 *     by reason of the employment (Sl. 3(c))
 * @param similarInstitutionCostPerMonth the cost of such education in a similar institution in or near the locality,
 *     per month
 * @param months the months in the tax year it was provided, 0 to 12
 * @param recovered what the employee paid or had recovered in the year
 */
public record EmployerChildEducation(
        int employment,
        boolean inEmployersInstitution,
        Money similarInstitutionCostPerMonth,
        int months,
        Money recovered) {

    public EmployerChildEducation {
        if (similarInstitutionCostPerMonth == null || recovered == null) {
            throw new IllegalArgumentException("A child's education needs the cost per month and the recovery");
        }
        if (employment < 0 || months < 0 || months > 12 || similarInstitutionCostPerMonth.isLessThan(Money.ZERO)
                || recovered.isLessThan(Money.ZERO)) {
            throw new IllegalArgumentException("A child's education needs a position, 0 to 12 months, and amounts of nil"
                    + " or more");
        }
    }
}
