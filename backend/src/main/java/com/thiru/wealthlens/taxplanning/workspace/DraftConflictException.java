package com.thiru.wealthlens.taxplanning.workspace;

import java.util.List;

/**
 * A save that cannot apply, because the draft moved on or is frozen; carries what a client needs to merge ({@code DR 11} F5, F10).
 */
public class DraftConflictException extends RuntimeException {

    private final transient DraftConflict conflict;

    DraftConflictException(String sentence, DraftView current, List<String> conflictingPaths) {
        super(sentence);
        this.conflict = new DraftConflict(sentence, current, conflictingPaths);
    }

    public DraftConflict conflict() {
        return conflict;
    }

    /**
     * What a 409 carries.
     *
     * @param message why it could not apply
     * @param current the draft as it stands
     * @param conflictingPaths the request's paths another save has changed since the revision it was made against
     */
    public record DraftConflict(String message, DraftView current, List<String> conflictingPaths) {
    }
}
