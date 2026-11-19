package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Optional;

/**
 * The furniture in furnished accommodation (S4 r.15(2)(e); Rules 1962 r.3(1) Table I col. 4; D-111): its cost, on
 * which ten per cent a year is added, or, where hired from a third party, the hire charges; less what the employee
 * pays for it.
 *
 * @param cost the cost of the furniture, "including television sets, radio sets, refrigerators, other household
 *     appliances, air-conditioning plant or equipment"
 * @param hireCharges the hire charges payable for the period, where the furniture is hired from a third party
 * @param employeeCharges what the employee paid or pays for it in the year
 */
public record Furniture(Optional<Money> cost, Optional<Money> hireCharges, Money employeeCharges) {

    public Furniture {
        if (cost == null || hireCharges == null || employeeCharges == null) {
            throw new IllegalArgumentException("Furniture needs its cost or hire charges, and the employee's charges");
        }
        if (cost.isPresent() == hireCharges.isPresent()) {
            throw new IllegalArgumentException("Furniture is either the employer's, at its cost, or hired, at its"
                    + " charges; give exactly one");
        }
        if (employeeCharges.isLessThan(Money.ZERO)
                || cost.or(() -> hireCharges).filter(amount -> amount.isLessThan(Money.ZERO)).isPresent()) {
            throw new IllegalArgumentException("Furniture's amounts may not be negative");
        }
    }

    /** The employer's own furniture, at its cost. */
    public static Furniture owned(Money cost, Money employeeCharges) {
        return new Furniture(Optional.ofNullable(cost), Optional.empty(), employeeCharges);
    }

    /** Furniture hired from a third party, at its hire charges. */
    public static Furniture hired(Money hireCharges, Money employeeCharges) {
        return new Furniture(Optional.empty(), Optional.ofNullable(hireCharges), employeeCharges);
    }
}
