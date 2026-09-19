package com.thiru.wealthlens.taxrules.id;

import java.util.regex.Pattern;

/**
 * A return form, such as {@code ITR-1} or {@code ITR-UN}.
 *
 * <p>The form set belongs to a tax year and earlier sets stay live: a return for an earlier year is
 * furnished in the form applicable in that year. There are nine, not four — ITR-1 to ITR-7 plus
 * ITR-V and the updated-return form.
 */
public record ItrFormCode(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[A-Z0-9-]{3,16}$");

    private static final String SHAPE =
            "upper-case, digits and hyphens, 3-16 characters, e.g. ITR-1 or SAHAJ";

    public ItrFormCode {
        value = Identifiers.require(value, PATTERN, "ItrFormCode", SHAPE);
    }

    @Override
    public String toString() {
        return value;
    }
}
