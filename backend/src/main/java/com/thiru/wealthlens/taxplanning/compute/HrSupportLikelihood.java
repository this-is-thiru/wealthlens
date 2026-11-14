package com.thiru.wealthlens.taxplanning.compute;

/**
 * How likely an employer is to agree to a change of one component or fund ({@code 03-advisory.md} A2.6
 * {@code hrSupportLikelihood}). A product judgement with no legal source: reported beside a change, never a veto (A2.2
 * C10). Kept from the legacy path so one concept has one word (W-238f C2a); W-159 retires that path, not this type.
 */
public enum HrSupportLikelihood {
    /** A common, flexible component that employers routinely re-split on request. */
    HIGH,
    /** Often offered, usually inside a flexible-benefit plan. */
    MEDIUM,
    /** Fixed by company policy; rarely changed for one employee. */
    LOW,
    /** Possible only where the employer has set up the scheme at all. */
    EMPLOYER_DISCRETION,
    /** Paid on an event or by a formula, so there is nothing to ask the employer for. */
    NOT_APPLICABLE
}
