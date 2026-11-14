package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.money.Money;

/**
 * A service, utility, education or carrier's journey the employer provided or paid for (S4 r.15(4) Table III Sl. 1,
 * 2, 3(a), 4; Rules 1962 r.3(3)–(6); D-120).
 *
 * @param employment which entered employment provided it, by position
 * @param kind what it is
 * @param arrangement whether the employer provided it or met the employee's own obligation
 * @param amount the amount the kind is valued on — the salary paid, the amount paid to the agency, units at the
 *     manufacturing cost, the expenditure, or the public fare
 * @param recovered what the employee paid or had recovered
 * @param carrierIsAirlineOrRailways for a carrier's journey, whether the employer is an airline or the railways
 */
public record EmployerHouseholdBenefit(
        int employment,
        HouseholdBenefitKind kind,
        BenefitArrangement arrangement,
        Money amount,
        Money recovered,
        boolean carrierIsAirlineOrRailways) {

    public EmployerHouseholdBenefit {
        if (kind == null || arrangement == null || amount == null || recovered == null) {
            throw new IllegalArgumentException("A household benefit needs its kind, arrangement, amount and recovery");
        }
        if (employment < 0 || amount.isLessThan(Money.ZERO) || recovered.isLessThan(Money.ZERO)) {
            throw new IllegalArgumentException("An employment position and benefit amounts may not be negative");
        }
        requireTheEmployersOwnSupplyIsNotAnObligation(kind, arrangement);
    }

    /** The employer's own supplies cannot meet the employee's obligation. */
    private static void requireTheEmployersOwnSupplyIsNotAnObligation(HouseholdBenefitKind kind,
            BenefitArrangement arrangement) {
        boolean employerOnly = kind == HouseholdBenefitKind.UTILITY_FROM_EMPLOYER_RESOURCES
                || kind == HouseholdBenefitKind.CARRIER_TRAVEL;
        if (employerOnly && arrangement == BenefitArrangement.EMPLOYEE_OBLIGATION_MET) {
            throw new IllegalArgumentException(kind + " is the employer's own supply; it cannot meet an obligation of"
                    + " the employee's");
        }
    }
}
