package com.thiru.wealthlens.taxengine.rules.temporal;

/**
 * Which clock a rule is read against.
 *
 * <p>Every rule lookup takes a temporal coordinate, and each rule declares which one. Keying
 * everything on the tax year is not enough: section 197(3) of the Income-tax Act 2025 turns on
 * whether land or building was <em>acquired before 23 July 2024</em>, so a computation for a tax
 * year decades hence still needs that date. A rule keyed on an asset cannot be found by a lookup
 * keyed on a year.
 *
 * <p>A node evaluating a rule on a non-{@link #TAX_YEAR} axis must supply that coordinate. Failing
 * to is an error, never a quiet fall back to today or to the year end.
 */
public enum TemporalAxis {

    /** The default. Slab tables, standard deduction, the deduction ceilings. */
    TAX_YEAR,

    /** When the transaction happened — a perquisite provided, rent paid for a period. */
    EVENT_DATE,

    /** When the asset was acquired. Section 197(3)'s 23 July 2024 split. */
    ACQUISITION_DATE,

    /** When a loan was sanctioned. The 80EE, 80EEA and 80EEB windows. */
    SANCTION_DATE,

    /** When a payment was made. The mode-of-payment tests for health insurance and donations. */
    PAYMENT_DATE,

    /** When the return was furnished. Form selection, late-filing interest and the late fee. */
    FILING_DATE
}
