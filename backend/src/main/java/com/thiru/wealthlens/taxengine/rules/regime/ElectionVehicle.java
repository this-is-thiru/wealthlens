package com.thiru.wealthlens.taxengine.rules.regime;

/**
 * Where the choice is recorded.
 *
 * <p>Not a detail. For TY 2026-27 the opt-out rides on the return itself — rule 136:
 * <em>&ldquo;The option to be exercised or withdrawn ... shall be <b>in the return of income</b> to
 * be furnished under section 263(1) for such tax year&rdquo;</em> — so there is <b>no separate
 * form</b>. A product that asks a user to file one is asking for something that does not exist.
 */
public enum ElectionVehicle {

    /** The choice is made in the return. Rule 136, VERIFIED for TY 2026-27. */
    IN_THE_RETURN,

    /** A separate prescribed form must be filed. */
    PRESCRIBED_FORM,

    /** No vehicle, because no election is made — the default. */
    NONE
}
