package com.thiru.wealthlens.portfolio.dto.enums;

/**
 * What {@code portfolio}'s records say about securities transaction tax on one side of a disposal (W-088j, D-292).
 *
 * <p>Three states, because "no record" is not "none levied": a disposal priced before the charge engine, or by the V1
 * sell, keeps no breakdown of its charges, and reading that as {@code NOT_PAID} once charged a listed-equity gain at
 * slab rates, silently (W-088f's F1). A fact, not a tax outcome: which states a special rate accepts is the tax side's
 * rule data.
 */
public enum SttEvidence {

    /** A breakdown is on record and levied securities transaction tax above nil. */
    PAID,

    /** A breakdown is on record and levied none. */
    NOT_PAID,

    /** No breakdown is on record, so whether it was levied is not known. */
    UNKNOWN
}
