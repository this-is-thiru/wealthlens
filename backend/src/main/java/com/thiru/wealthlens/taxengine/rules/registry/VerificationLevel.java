package com.thiru.wealthlens.taxengine.rules.registry;

/**
 * How sure we are that a value was transcribed right — the second of the three status axes (D-036).
 *
 * <p>This is rendered to the user, not kept for the team. A figure resting on an unverified source
 * is marked at the point it is used, because the alternative — a footnote nobody reads — is how a
 * provisional number becomes a filed one.
 */
public enum VerificationLevel {
    /**
     * Not confirmed against the primary source. The row must say what to check and where; a pack
     * holding one without that fails to load (invariant I1).
     */
    UNVERIFIED,
    /** Read against the gazette text. */
    SOURCE_CHECKED,
    /** Signed off by a chartered accountant. */
    PROFESSIONALLY_REVIEWED
}
