package com.thiru.wealthlens.taxengine.rules.registry;

import java.net.URI;
import java.time.LocalDate;

/**
 * Where a rule's value came from, and how far it has been confirmed.
 *
 * <p>Every rule row carries one. It is not documentation: the verification level reaches the user at the point
 * the figure is used, and the whole set is the queue that says which verification work is worth
 * doing next.
 *
 * @param sourceUrl the document actually opened, not a search result or a summary
 * @param sourceDescription enough to find the passage again, e.g. "Gazette CG-DL-E-20032026-271092,
 *     rule 279(1)(c) Table"
 * @param verificationLevel how far the reading has been confirmed
 * @param whatToCheck what a verifier should open and what to look for; required when the level is
 *     {@link VerificationLevel#UNVERIFIED}
 */
public record Provenance(
        URI sourceUrl,
        String sourceDescription,
        LocalDate accessedOn,
        VerificationLevel verificationLevel,
        String whatToCheck) {

    /**
     * Invariant I1: an unverified row must say what to check.
     *
     * <p>Enforced here rather than only at pack load, because it is a property of the record's own
     * fields and needs no other context. A row that cannot say what would settle it is a row nobody
     * will ever settle.
     */
    public Provenance {
        if (verificationLevel == null) {
            throw new IllegalArgumentException("Provenance.verificationLevel may not be null");
        }
        if (verificationLevel == VerificationLevel.UNVERIFIED && isBlank(whatToCheck)) {
            throw new IllegalArgumentException(
                    "Provenance at " + verificationLevel + " must say whatToCheck, but it was "
                            + (whatToCheck == null ? "null" : "blank")
                            + " (invariant I1, sourceDescription=\"" + sourceDescription + "\")");
        }
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    /** Whether a figure derived from this rule must be flagged where the user sees it. */
    public boolean requiresUserVisibleFlag() {
        return verificationLevel == VerificationLevel.UNVERIFIED;
    }
}
