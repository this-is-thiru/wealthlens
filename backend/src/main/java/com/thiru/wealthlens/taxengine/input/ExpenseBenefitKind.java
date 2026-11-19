package com.thiru.wealthlens.taxengine.input;

/**
 * An employer's expenditure that Table IV values at its amount, less what the employee paid (S4 r.15(5) Table IV
 * Sl. 2, 5, 6, 9; Rules 1962 r.3(7)(ii), (v), (vi), (ix); D-119).
 */
public enum ExpenseBenefitKind {

    /** Travel, touring, accommodation and other expenses for a holiday, other than leave travel concession. */
    HOLIDAY,

    /** A holiday facility the employer maintains and does not offer to all employees alike: its public value. */
    HOLIDAY_OWN_FACILITY_AT_PUBLIC_VALUE,

    /** Expenses on a household member accompanying the employee on official tour. */
    FAMILY_ON_OFFICIAL_TOUR,

    /** An official tour extended as a vacation: the expenses of the extension only. */
    TOUR_EXTENDED_AS_VACATION,

    /** Expenses, membership and annual fees charged to a card the employer provides, or paid by it. */
    CREDIT_CARD,

    /** Club expenditure, annual or periodical fees included, a corporate membership's initial fee excluded. */
    CLUB,

    /** Any other benefit, amenity, service, right or privilege, at its arm's-length cost to the employer. */
    OTHER,

    /** Telephone expenses, a mobile phone's included — excluded in both years. */
    TELEPHONE
}
