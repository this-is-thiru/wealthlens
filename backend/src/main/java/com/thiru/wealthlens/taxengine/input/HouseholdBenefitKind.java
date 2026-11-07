package com.thiru.wealthlens.taxengine.input;

/** A Table III benefit valued at an entered amount (S4 r.15(4) Table III; Rules 1962 r.3(3)–(6); D-120). */
public enum HouseholdBenefitKind {

    /** A sweeper, gardener, watchman or personal attendant: the salary paid for the services. */
    SERVICES,

    /** Gas, electric energy or water bought from an outside agency: the amount paid to the agency. */
    UTILITY_FROM_OUTSIDE_AGENCY,

    /** Gas, electric energy or water from the employer's own resources: units at its manufacturing cost per unit. */
    UTILITY_FROM_EMPLOYER_RESOURCES,

    /** Free or concessional education for a household member: the employer's expenditure. */
    EDUCATION_FEES,

    /** A carrier's free or concessional journey: the value at which it is offered to the public. */
    CARRIER_TRAVEL
}
