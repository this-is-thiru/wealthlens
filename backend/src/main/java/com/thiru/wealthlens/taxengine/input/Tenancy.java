package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;

/**
 * A home the taxpayer occupied for a period, with the rent paid for that period (D-099).
 *
 * <p>HRA's exemption is worked per period of occupation, not on annual figures ({@code DR 13} R-11; S4 r.279(2)(a):
 * the relevant period is "the period during which the said accommodation was occupied"), and a move changes the
 * location's percentage mid-year. So each tenancy carries its own window, place and rent.
 *
 * @param occupied the days the home was occupied, both ends inclusive
 * @param location a location code from the year's HRA table, or {@code ANY_OTHER_PLACE}; the rule checks it
 * @param ownedByAssessee whether the taxpayer owns the home — then no exemption (Sch. III Table Sl. 11(c))
 * @param rentPaid the rent actually paid for {@code occupied}
 */
public record Tenancy(DateRange occupied, String location, boolean ownedByAssessee, Money rentPaid) {

    public Tenancy {
        if (occupied == null || rentPaid == null) {
            throw new IllegalArgumentException("A tenancy needs the period occupied and the rent paid for it");
        }
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("A tenancy needs a location code, or ANY_OTHER_PLACE");
        }
        if (rentPaid.isLessThan(Money.ZERO)) {
            throw new IllegalArgumentException("A tenancy's rent paid may not be negative, but was " + rentPaid);
        }
    }
}
