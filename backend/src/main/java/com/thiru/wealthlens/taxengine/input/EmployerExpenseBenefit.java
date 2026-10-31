package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.money.Money;

/**
 * An expenditure an employer met for the employee or a household member that Table IV values at its amount (S4
 * r.15(5) Table IV Sl. 2, 5, 6, 9; Rules 1962 r.3(7)(ii), (v), (vi), (ix); D-119).
 *
 * <p>The amount entered is the one the kind is valued on — the expenditure, a facility's public value, or a tour
 * extension's expenses. A credit card or club bill is nil only on the facts the year's Rule names: wholly for work
 * with the employer's records and certificate, and for a club the uniform health-club facilities (OQ-12).
 *
 * @param employment which entered employment met it, by position
 * @param kind what it is
 * @param amount the amount the kind is valued on
 * @param recovered what the employee paid or had recovered
 * @param officialUse the employer's details and certificate, for a credit card or club bill
 * @param whollyForWork for a credit card, wholly for official purposes; for a club, wholly for business purposes
 * @param healthClubUniformForAll for a club, whether health-club, sports and similar facilities are provided to all
 *     employees alike
 */
public record EmployerExpenseBenefit(
        int employment,
        ExpenseBenefitKind kind,
        Money amount,
        Money recovered,
        OfficialUseRecord officialUse,
        boolean whollyForWork,
        boolean healthClubUniformForAll) {

    public EmployerExpenseBenefit {
        if (kind == null || amount == null || recovered == null || officialUse == null) {
            throw new IllegalArgumentException("An expense benefit needs its kind, amount, recovery and records");
        }
        if (employment < 0 || amount.isLessThan(Money.ZERO) || recovered.isLessThan(Money.ZERO)) {
            throw new IllegalArgumentException("An employment position and expense amounts may not be negative");
        }
    }
}
