package com.thiru.wealthlens.taxengine.input;

/**
 * The employee's standing in an employer that is a company — present on an {@link Employment} only where the
 * employer is one (D-113).
 *
 * <p>A benefit or amenity is a perquisite for every employee of a company who is a director of it or has a
 * substantial interest in it, whatever the salary (S2 s.17(1)(c)(i); 1961 s.17(2)(iii)(a)–(b)); both limbs read
 * "by a company", so an employer that is not one carries neither fact.
 *
 * @param isDirector whether the employee is a director of the company
 * @param hasSubstantialInterest whether the employee has a substantial interest in the company, as the Act defines
 *     it
 */
public record CompanyEmployer(boolean isDirector, boolean hasSubstantialInterest) {

    /** Whether a benefit from this company is a perquisite whatever the employee's salary. */
    public boolean makesEveryBenefitAPerquisite() {
        return isDirector || hasSubstantialInterest;
    }
}
