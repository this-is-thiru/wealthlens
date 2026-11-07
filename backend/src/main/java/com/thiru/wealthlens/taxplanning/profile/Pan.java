package com.thiru.wealthlens.taxplanning.profile;

import java.util.regex.Pattern;

/**
 * A permanent account number. A type rather than a string so that masking is the default: PAN is an identifier, never logged or
 * put in a message in full ({@code DR 14} O1.1; {@code DR 15} F1).
 */
public record Pan(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[A-Z]{5}[0-9]{4}[A-Z]$");

    public Pan {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("A PAN is five capital letters, four digits and a capital letter");
        }
    }

    /** The first three characters and the last two, the rest hidden. */
    public String masked() {
        return value.substring(0, 3) + "XXXXX" + value.substring(8);
    }

    /** {@code value} masked, or nothing where none is held, for a response that must not carry it in full. */
    static String maskedOrNull(String value) {
        return value == null ? null : new Pan(value).masked();
    }
}
