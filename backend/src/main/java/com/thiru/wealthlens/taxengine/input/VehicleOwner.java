package com.thiru.wealthlens.taxengine.input;

/** Whose vehicle it is — the three rows of Table II (S4 r.15(3); Rules 1962 r.3(2); D-113). */
public enum VehicleOwner {

    /** A motor car the employer owns or hires (Sl. 1). */
    EMPLOYER_CAR,

    /** The employee's own motor car, run at the employer's cost (Sl. 2). */
    EMPLOYEE_CAR,

    /** The employee's own other automotive conveyance, run at the employer's cost (Sl. 3). */
    EMPLOYEE_OTHER_CONVEYANCE
}
