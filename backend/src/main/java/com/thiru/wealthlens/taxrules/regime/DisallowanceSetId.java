package com.thiru.wealthlens.taxrules.regime;

import java.util.regex.Pattern;

/**
 * A named set of disallowances shared between regimes.
 *
 * <p>The s.205(1) pattern. Several regimes disallow the same list of deductions, and the list is long; referencing it by id means one definition and one place to change it, rather than the same forty entries copied into each regime and drifting apart.
 *
 * @param value e.g. {@code "DIS-S202-2A"}
 */
public record DisallowanceSetId(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[A-Z][A-Z0-9_-]{1,63}$");

    public DisallowanceSetId {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "A DisallowanceSetId must be upper-case with hyphens or underscores, 2-64 characters,"
                            + " e.g. DIS-S202-2A, but was \"" + value + "\"");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
