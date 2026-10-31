package com.thiru.wealthlens.taxengine.rules.registry;

/**
 * What the law says about one rule version — the first of the three status axes (D-036).
 *
 * <p>Set by the rule author from the source. It is kept apart from {@link VerificationLevel} and
 * {@link PublicationStatus} because a Budget proposal can be read perfectly and still not be law,
 * and a law can be in force and still be transcribed wrong.
 *
 * <p>Which versions apply on a date is still decided by the version's {@code Validity} alone; what
 * {@link #WITHDRAWN} and {@link #STRUCK_DOWN} mean for dates is to be sourced first (D-052).
 */
public enum LegalStatus {
    /** Proposed in a Finance Bill; not law. */
    BILL,
    /** Passed and assented; not yet in force. */
    ENACTED,
    /** Subordinate legislation notified in the gazette. */
    NOTIFIED,
    /**
     * Enacted but awaiting a notification to take effect, with a stated interim position. Only
     * valid with a {@code Validity.PendingNotification}, which names the interim rule.
     */
    PENDING_NOTIFICATION,
    /** In force. */
    IN_FORCE,
    /** Withdrawn by the legislature or the government. */
    WITHDRAWN,
    /** Held invalid by a court. */
    STRUCK_DOWN
}
