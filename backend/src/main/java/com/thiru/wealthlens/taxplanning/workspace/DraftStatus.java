package com.thiru.wealthlens.taxplanning.workspace;

import java.util.Map;
import java.util.Set;

/** Where a draft stands ({@code DR 11} F3). {@code ACTED_ON} is the user's assertion that they filed on it; we never file. */
public enum DraftStatus {
    DRAFT,
    READY,
    ACTED_ON,
    ARCHIVED;

    private static final Map<DraftStatus, Set<DraftStatus>> NEXT = Map.of(
            DRAFT, Set.of(READY, ARCHIVED),
            READY, Set.of(DRAFT, ACTED_ON, ARCHIVED),
            ACTED_ON, Set.of(ARCHIVED),
            ARCHIVED, Set.of(DRAFT));

    /** Whether a draft here may become {@code next}. */
    public boolean canBecome(DraftStatus next) {
        return NEXT.get(this).contains(next);
    }

    /** Whether its inputs may change: a filed draft's are frozen, since later years read them ({@code DR 11} F3). */
    public boolean isEditable() {
        return this == DRAFT || this == READY;
    }
}
