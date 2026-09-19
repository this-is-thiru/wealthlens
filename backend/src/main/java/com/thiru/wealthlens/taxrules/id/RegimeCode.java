package com.thiru.wealthlens.taxrules.id;

import java.util.regex.Pattern;

/**
 * One set of rates and disallowances a taxpayer may be computed under, such as
 * {@code S202_DEFAULT}.
 *
 * <p>Not an enum, and not two values. The Income-tax Act 2025 already carries five concurrently
 * in-force regime sections, and a regime has a lifetime: it may close to new entrants while
 * remaining in force for those already in it. The count belongs to the rule pack.
 */
public record RegimeCode(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{1,31}$");

    private static final String SHAPE =
            "upper-case with underscores, 2-32 characters, e.g. S202_DEFAULT";

    public RegimeCode {
        value = Identifiers.require(value, PATTERN, "RegimeCode", SHAPE);
    }

    @Override
    public String toString() {
        return value;
    }
}
