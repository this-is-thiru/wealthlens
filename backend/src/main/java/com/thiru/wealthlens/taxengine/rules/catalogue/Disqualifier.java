package com.thiru.wealthlens.taxengine.rules.catalogue;

/** One thing that stops a section applying, typed by when it bites and linked to what enforces it. */
public record Disqualifier(DisqualifierKind kind, String description, EnforcedBy enforcedBy) {

    public Disqualifier {
        if (kind == null || enforcedBy == null) {
            throw new IllegalArgumentException("A disqualifier must state its kind and what enforces it");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("A disqualifier must have a description");
        }
    }
}
