package com.thiru.wealthlens.taxplanning.profile;

/**
 * A tax deduction account number: an identifier, never logged or put in a message in full ({@code DR 14} O1.1). Only its presence
 * is checked: the repo's sources state no format for it, and none is invented here.
 */
public record Tan(String value) {

    public Tan {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("An employer needs its TAN");
        }
    }

    /** The first three characters at most, the rest hidden. */
    public String masked() {
        return value.substring(0, Math.min(3, value.length())) + "XXXXXXX";
    }
}
