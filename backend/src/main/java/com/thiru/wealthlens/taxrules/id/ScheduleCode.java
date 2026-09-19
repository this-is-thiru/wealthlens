package com.thiru.wealthlens.taxrules.id;

import java.util.regex.Pattern;

/**
 * A schedule within a return form, such as {@code SCH-S}.
 *
 * <p>Schedules are defined once and referenced by several forms, the way the Act shares one
 * disallowance list across five regime sections. Schedule S is filled the same way whether it
 * appears in ITR-1, ITR-2 or ITR-3, so a new form that reuses it needs no export code.
 */
public record ScheduleCode(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[A-Z0-9-]{3,16}$");

    private static final String SHAPE =
            "upper-case, digits and hyphens, 3-16 characters, e.g. SCH-S";

    public ScheduleCode {
        value = Identifiers.require(value, PATTERN, "ScheduleCode", SHAPE);
    }

    @Override
    public String toString() {
        return value;
    }
}
