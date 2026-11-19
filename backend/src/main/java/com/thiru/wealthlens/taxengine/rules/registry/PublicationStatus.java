package com.thiru.wealthlens.taxengine.rules.registry;

/**
 * Where a rule version is in our own workflow — the third of the three status axes (D-036).
 *
 * <p>Only {@link #PUBLISHED} versions enter a rule pack; a pack holding any other is refused. A
 * {@link #DRAFT} of a Budget proposal can power a clearly labelled preview without ever touching a
 * real computation.
 */
public enum PublicationStatus {
    DRAFT,
    IN_REVIEW,
    PUBLISHED,
    /**
     * A transcription replaced by a corrected one (D-052). Not an older version of the law: that
     * stays {@link #PUBLISHED}, because an earlier year's pack still needs it.
     */
    SUPERSEDED
}
