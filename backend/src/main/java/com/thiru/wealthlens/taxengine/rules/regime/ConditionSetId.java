package com.thiru.wealthlens.taxengine.rules.regime;

import java.util.regex.Pattern;

/**
 * A named set of eligibility conditions shared between regimes.
 *
 * <p>The s.205(2) pattern, and the counterpart to {@link DisallowanceSetId}. Eligibility is referenced by id for the same reason: shared, defined once, changed in one place.
 *
 * @param value e.g. {@code "ELIG-S205-2"}
 */
public record ConditionSetId(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[A-Z][A-Z0-9_-]{1,63}$");

    public ConditionSetId {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "A ConditionSetId must be upper-case with hyphens or underscores, 2-64 characters,"
                            + " e.g. ELIG-S205-2, but was \"" + value + "\"");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
