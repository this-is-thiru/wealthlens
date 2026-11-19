package com.thiru.wealthlens.taxengine.rules.id;

import java.util.regex.Pattern;

/**
 * A retirement fund an employer contributes to, such as {@code NOTIFIED_PENSION_SCHEME} (W-238e). The rules read it as
 * the item fact {@code employer.fund}; the inputs name it by {@code EmployerFund}, whose names these codes are, since the
 * rules vocabulary may not depend on the inputs.
 */
public record EmployerFundCode(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{1,63}$");

    private static final String SHAPE =
            "upper-case with underscores, 2-64 characters, e.g. NOTIFIED_PENSION_SCHEME";

    public EmployerFundCode {
        value = Identifiers.require(value, PATTERN, "EmployerFundCode", SHAPE);
    }

    @Override
    public String toString() {
        return value;
    }
}
