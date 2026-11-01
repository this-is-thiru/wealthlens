package com.thiru.wealthlens.taxplanning.workspace;

/** A save made against a revision that is no longer current; carries what is, so a client can merge ({@code DR 11} F5). */
public class StaleDraftException extends RuntimeException {

    private final transient DraftView current;

    StaleDraftException(DraftView current) {
        super("This draft has moved on to revision " + current.revision());
        this.current = current;
    }

    public DraftView current() {
        return current;
    }
}
