package com.thiru.wealthlens.taxrules.registry;

/**
 * How far a rule's value has been confirmed against a primary source.
 *
 * <p>This is rendered to the user, not kept for the team. A figure resting on an unverified rule is
 * marked at the point it is used, because the alternative — a footnote nobody reads — is how a
 * provisional number becomes a filed one.
 */
public enum VerificationStatus {

    /** The primary source was opened and says this. */
    VERIFIED,

    /**
     * Not confirmed against a primary source. The row must say what to check and where; the pack
     * fails to load otherwise.
     */
    NEEDS_VERIFICATION,

    /**
     * The source was opened and is ambiguous or self-inconsistent. The computation states which
     * reading it used and why.
     */
    STATUS_UNCLEAR
}
