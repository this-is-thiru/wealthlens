package com.thiru.wealthlens.taxengine.rules.id;

import java.util.regex.Pattern;

/**
 * A cell of a return form's schedules, such as {@code SALARY_GROSS}: one figure, defined once with its label and source
 * and placed by each form at its own path (W-092 C2a, D-654). ITR-1 prints gross salary at B1(i), ITR-2 at Schedule S
 * item 2; both name this one cell, so the two forms cannot show it differently.
 */
public record CellCode(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{1,63}$");

    private static final String SHAPE = "upper-case with underscores, 2-64 characters, e.g. SALARY_GROSS";

    public CellCode {
        value = Identifiers.require(value, PATTERN, "CellCode", SHAPE);
    }

    @Override
    public String toString() {
        return value;
    }
}
