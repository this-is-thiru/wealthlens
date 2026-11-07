package com.thiru.wealthlens.taxengine.rules.definition;

/**
 * The arithmetic Table II values a vehicle by (S4 r.15(3); Rules 1962 r.3(2); D-113) — named for the computation, not
 * the Table's serial, so a year that renumbers or re-reaches its serials changes only the conditions of
 * {@code RS-PERQ-CAR-PATH}'s rows (W-154e2, D-330). Closed: a new computation is new arithmetic.
 */
public enum VehicleComputation {

    /** Wholly official with the employer's records: no value. */
    NOT_A_PERQUISITE,

    /** The employer's car used partly privately, the employer meeting the running: a monthly amount by engine. */
    PER_MONTH_EMPLOYER_RUNNING,

    /** The employer's car used partly privately, the employee meeting the private running: a monthly amount. */
    PER_MONTH_EMPLOYEE_RUNNING,

    /** The employer's car used wholly privately: the running, plus wear and tear on the cost, less the charge. */
    RUNNING_AND_WEAR_LESS_CHARGE,

    /** The employee's own car: the expenditure less the employer-runs monthly amount for its engine, or a higher claim. */
    OWN_CAR_EXPENDITURE_LESS_TABLE_AMOUNT,

    /** The employee's own other conveyance: the expenditure less a monthly sum, or a higher claim. */
    OWN_CONVEYANCE_EXPENDITURE
}
