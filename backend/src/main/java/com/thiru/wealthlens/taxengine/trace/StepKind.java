package com.thiru.wealthlens.taxengine.trace;

/**
 * What kind of thing a trace line did.
 *
 * <p>This is the axis the PDF groups by ({@code 06-algorithms.md} G7.3), which is why it is a closed
 * enum rather than a string from the pack: a renderer that meets an unknown kind has no sensible
 * section to put it in.
 *
 * <p>The seven values are {@code 05-domain-model.md} M5.1's list, unchanged. They describe the
 * <b>step</b>, not the head of income — {@code EXEMPTION} and {@code DEDUCTION} are different
 * operations under the Act and reach the total in different places, so collapsing them would lose
 * the distinction the trace exists to show.
 */
public enum StepKind {

    /** An amount entering the computation — gross salary, a capital gain, interest received. */
    INCOME,

    /** An amount taken out before a head's total is struck — HRA, gratuity, leave encashment. */
    EXEMPTION,

    /** An amount subtracted from gross total income — Chapter VI-A, the standard deduction. */
    DEDUCTION,

    /** A loss set against income, within a head or across heads. */
    SET_OFF,

    /** Tax charged — the slab ladder, a special rate, surcharge, cess. */
    TAX,

    /** Tax taken off after it was charged — rebate, marginal relief, double-taxation relief. */
    RELIEF,

    /** Rounding applied under s.516, kept as its own step so the adjustment is visible. */
    ROUNDING
}
