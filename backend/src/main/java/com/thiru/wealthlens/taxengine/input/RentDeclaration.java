package com.thiru.wealthlens.taxengine.input;

/**
 * The Form No. 31 declaration that claims the deduction for rent paid (S2 s.134; S4 r.65; D-141). Entering it is the
 * claim; the rent is the tenancies'. It carries what the tenancies cannot: the months of stay Form 31 asks for, which
 * the ₹5000-a-month limb counts (OQ-22), and the two ownership bars of s.134(4)(a).
 *
 * @param monthsOfStay the period of stay in months, as Form 31 item 4 states it — 1 to 12
 * @param ownsHomeWhereResidesOrWorks whether the assessee, spouse, minor child or the assessee's Hindu undivided
 *     family owns residential accommodation at the place where the assessee ordinarily resides, works or carries on
 *     business (s.134(4)(a)(i))
 * @param ownsOccupiedHomeElsewhere whether the assessee owns, at any other place, accommodation in the assessee's
 *     occupation valued under s.21(6) or (7)(a) (s.134(4)(a)(ii))
 */
public record RentDeclaration(int monthsOfStay, boolean ownsHomeWhereResidesOrWorks,
        boolean ownsOccupiedHomeElsewhere) {

    public RentDeclaration {
        if (monthsOfStay < 1 || monthsOfStay > 12) {
            throw new IllegalArgumentException("A rent declaration's months of stay are 1 to 12, but were "
                    + monthsOfStay);
        }
    }
}
