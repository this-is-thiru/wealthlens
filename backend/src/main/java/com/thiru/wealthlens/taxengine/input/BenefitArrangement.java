package com.thiru.wealthlens.taxengine.input;

/**
 * Whether the employer provides a Table III benefit itself or meets the employee's own obligation — which decides whom
 * it is a perquisite for (S2 s.17(1)(c), (f); 1961 s.17(2)(iii), (iv); D-120, OQ-10).
 */
public enum BenefitArrangement {

    /** The employer's own servant, supply or institution: a benefit or amenity, a specified employee's perquisite. */
    EMPLOYER_PROVIDED,

    /** The employee's own servant, connection or fees, paid by the employer: every employee's perquisite. */
    EMPLOYEE_OBLIGATION_MET
}
