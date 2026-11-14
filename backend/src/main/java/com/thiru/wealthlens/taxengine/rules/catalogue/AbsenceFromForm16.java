package com.thiru.wealthlens.taxengine.rules.catalogue;

/** What it means for a claim when the employer's certificate does not show it ({@code DR 22} C2). */
public enum AbsenceFromForm16 {

    /** Nothing is lost: the return takes the claim. */
    CLAIMABLE_IN_RETURN,

    /** Not allowed until the other party reports it. */
    NOT_ALLOWABLE_UNLESS_REPORTED,

    /** The employer did not provide it, so there is nothing to claim. */
    NOTHING_TO_CLAIM,

    /** Waits for a certificate that does not exist yet. */
    BLOCKED_UNTIL_CERTIFIED
}
