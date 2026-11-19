package com.thiru.wealthlens.taxengine.rules.pack;

/** How many of a rule set's rows a node takes, declared by the set in the pack (W-088m1, D-252). */
public enum RuleSelection {

    /** One row, always: the only row, or the one that applies; none or several is a pack defect. */
    EXACTLY_ONE,

    /**
     * A row the year may not hold at all — a provision of one year only — or an item no row is for (D-253), else as
     * {@link #EXACTLY_ONE}.
     */
    AT_MOST_ONE,

    /** Every row, in the set's stated order; the node applies each that the regime allows. */
    ALL_THAT_APPLY
}
