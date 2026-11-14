package com.thiru.wealthlens.taxengine.rules.definition;

/**
 * When dearness allowance is part of a salary base — the Acts ask two different questions (D-092, D-111).
 *
 * <p>HRA, gratuity, leave encashment and the pension limits count DA "if the terms of employment so provide" (S4
 * r.279(2)(b); S2 s.19(2)(b)); the accommodation perquisite's "salary" leaves it out "unless it enters into the
 * computation of superannuation or retirement benefits" (S4 r.15(8)(k)(i); Rules 1962 r.3 Explanation (vi)(a)). Each
 * is a fact about the employment, and a base names which one it asks.
 */
public enum DearnessAllowanceTest {

    /** Counted where the terms of employment provide for it. */
    WHERE_TERMS_PROVIDE,

    /** Counted where it enters the computation of superannuation or retirement benefits. */
    WHERE_IT_COUNTS_FOR_RETIREMENT
}
