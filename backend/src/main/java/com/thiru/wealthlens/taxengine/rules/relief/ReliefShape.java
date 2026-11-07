package com.thiru.wealthlens.taxengine.rules.relief;

/**
 * A statutory relief — an amount the law takes off tax already computed — as one of a closed set of
 * shapes ({@code DR 10} E5, D-073).
 *
 * <p>The Acts state their reliefs as a few formulas over different numbers: the two rebates are one
 * shape, rebate marginal relief another, and the surcharge marginal relief the Finance Act states four
 * times with four ladders is a third. Holding them as shapes keeps each implemented once (D-ONEIMPL)
 * and lets a rule pack parameterise them. A new relief formula is a new class and a new permit — never
 * an edit to an existing shape or to a pipeline step. Composing reliefs, such as capping a rebate at the
 * tax, is the node's job, not a shape's.
 */
public sealed interface ReliefShape permits FlatWithCeiling, ExcessOverThreshold, ThresholdLadderCeiling, NoRelief {

    /** The amount to take off, with the reason the trace will show. */
    ReliefResult apply(ReliefContext ctx);
}
