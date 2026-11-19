package com.thiru.wealthlens.taxengine.rules.component;

/**
 * What sort of thing a component is.
 *
 * <p>An attribute the pack declares, never something engine code branches on. Observation O-25 was a
 * switch over a code that no longer existed; the fix is not a better enum but no switches at all —
 * semantics are declared here and read, so a component added next year behaves correctly without a
 * new branch.
 */
public enum ComponentKind {

    /** Pay: basic, dearness allowance, house rent allowance, bonus, commission. */
    SALARY,

    /** A benefit provided rather than paid — accommodation, a car, meals, shares. */
    PERQUISITE,

    /** What the employer puts into a retirement fund. */
    EMPLOYER_CONTRIBUTION,

    /** What the employee puts in — reduces cash, is not salary income. */
    EMPLOYEE_CONTRIBUTION,

    /** Withheld by statute rather than chosen, such as professional tax. */
    STATUTORY_DEDUCTION,

    /**
     * A sum paid that Chapter VIII lets the payer deduct — a Schedule XV investment, a pension deposit for a minor
     * (S2 s.123, s.124(4); D-130). It is income under no head, so it is the one kind without one.
     */
    QUALIFYING_PAYMENT
}
