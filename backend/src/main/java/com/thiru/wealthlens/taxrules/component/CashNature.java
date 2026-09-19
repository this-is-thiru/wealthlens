package com.thiru.wealthlens.taxrules.component;

/**
 * Whether a component is money in hand — {@code D-CASHFLAG}.
 *
 * <p>Three states, and conflating any two of them is a reported bug. H-15 and observation O-13 were
 * a take-home figure computed as gross minus deductions, where gross included the annualised value
 * of a company car and its driver: the user was told they take home money that never existed.
 *
 * <p>The fix is that take-home sums only {@link #CASH} components, so there is no path by which a
 * motor-car perquisite reaches it. {@link #CASH_OUTFLOW} is the third state that makes this work:
 * an employee provident fund contribution <em>reduces</em> cash but is not salary income, so it can
 * be neither of the other two.
 *
 * <p>This is a pack attribute rather than a Java field, which is what lets a component added next
 * year be excluded from take-home without the filter changing.
 */
public enum CashNature {

    /** Money received. Counts toward take-home. */
    CASH,

    /** A benefit in kind. Taxable, but never take-home. */
    NON_CASH,

    /** Money paid out. Reduces take-home without being income. */
    CASH_OUTFLOW
}
