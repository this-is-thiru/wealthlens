package com.thiru.wealthlens.taxengine.rules.id;

import java.util.regex.Pattern;

/**
 * One line of pay or payment, such as {@code EMPLOYER_PF}.
 *
 * <p>Component codes live in the rule pack rather than in a Java enum, so that adding an allowance
 * is a data change. Typo safety comes from the loader instead of the compiler: a code a rule
 * references must exist in the pack's registry, a code in a request must resolve, and the sum for
 * an income head runs over a declared partition — so an unrecognised code is an error rather than a
 * silent addend.
 */
public record ComponentCode(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{1,63}$");

    private static final String SHAPE =
            "upper-case with underscores, 2-64 characters, e.g. EMPLOYER_PF";

    public ComponentCode {
        value = Identifiers.require(value, PATTERN, "ComponentCode", SHAPE);
    }

    @Override
    public String toString() {
        return value;
    }
}
