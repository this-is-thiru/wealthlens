package com.thiru.wealthlens.taxengine.rules.id;

import java.util.regex.Pattern;

/** A part or schedule of a return form as the form names it, such as {@code S}, {@code HP} or {@code PART-B-TI} (W-092). */
public record ScheduleCode(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[A-Z0-9-]{1,16}$");

    private static final String SHAPE = "upper-case, digits and hyphens, 1-16 characters, e.g. S or PART-B-TI";

    public ScheduleCode {
        value = Identifiers.require(value, PATTERN, "ScheduleCode", SHAPE);
    }

    @Override
    public String toString() {
        return value;
    }
}
