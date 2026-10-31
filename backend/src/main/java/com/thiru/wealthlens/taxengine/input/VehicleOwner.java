package com.thiru.wealthlens.taxengine.input;

/** Whose vehicle it is — the three rows of Table II (S4 r.15(3); Rules 1962 r.3(2); D-113). */
public enum VehicleOwner {

    /** A motor car the employer owns or hires (Sl. 1). */
    EMPLOYER_CAR,

    /** The employee's own motor car, run at the employer's cost (Sl. 2). */
    EMPLOYEE_CAR,

    /** The employee's own other automotive conveyance, run at the employer's cost (Sl. 3). */
    EMPLOYEE_OTHER_CONVEYANCE;

    /**
     * Who provides the vehicle, as its owner means it: a car the employer owns or hires is the employer's provision; the
     * employee's own vehicle run at the employer's cost meets the employee's obligation. This is the owner's meaning, not
     * a legal choice — whether an arrangement needs a specified employee is {@code PERQ-SPECIFIED-EMPLOYEE}'s condition
     * (D-330 C4a).
     */
    public BenefitArrangement arrangement() {
        return switch (this) {
            case EMPLOYER_CAR -> BenefitArrangement.EMPLOYER_PROVIDED;
            case EMPLOYEE_CAR, EMPLOYEE_OTHER_CONVEYANCE -> BenefitArrangement.EMPLOYEE_OBLIGATION_MET;
        };
    }
}
