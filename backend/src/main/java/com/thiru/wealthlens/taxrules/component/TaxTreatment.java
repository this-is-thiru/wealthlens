package com.thiru.wealthlens.taxrules.component;

/**
 * How a component is treated for tax, declared rather than inferred.
 */
public enum TaxTreatment {

    /** Charged in the year it arises. */
    TAXABLE,

    /** Not charged now — an employer retirement contribution within its limits. */
    DEFERRED,

    /** Reduces taxable income, subject to the rules that consume it. */
    DEDUCTIBLE
}
