package com.thiru.wealthlens.brokercharges.dto.enums;

/**
 * How a charge's amount is arrived at.
 *
 * <p>Each constant maps to exactly one {@code ChargeCalculator} implementation, resolved through
 * {@code ChargeCalculatorRegistry}. Adding a new kind of charge arithmetic means adding a constant
 * here and one calculator class; no existing calculator changes.
 */
public enum ChargeBasis {

    /** A percentage of the context amount named by the rule's {@code amountBasis}. */
    TURNOVER,

    /** A fixed amount per chargeable event, independent of trade size. */
    FLAT,

    /** A fixed amount multiplied by quantity — per share, or per lot in derivatives. */
    PER_UNIT,

    /** A tiered rate selected by turnover band. See {@code ChargeSlab}. */
    SLAB,

    /**
     * A fixed amount charged at most once per {@code DedupeScope}. Depository (DP) charges use
     * this: levied once per scrip per day however many sell transactions occur.
     */
    SCOPED_FLAT,

    /**
     * A percentage of the summed amounts of other charges, named by the rule's {@code baseCodes}.
     * GST uses this. Naming the base explicitly is what prevents tax being applied to STT and
     * stamp duty, which are not GST-able.
     */
    DERIVED,

    /**
     * A SpEL expression over the charge context and the running accumulator, for charges whose
     * amount depends on a runtime fact no fixed field can express — mutual fund exit load, which
     * varies by holding period, is the motivating case.
     */
    FORMULA
}
