package com.thiru.wealthlens.taxengine.rules.id;

import java.util.regex.Pattern;

/**
 * One question the person answers and the engine cannot compute, such as whether they were a director in a company
 * (W-090a1, D-517). The questions are rows in the pack, not a Java enum, so a question the law adds is a data row; the
 * code lives here, beside the other identities, because the rules read it and the inputs name it, and the rules
 * vocabulary may not depend on the inputs.
 */
public record DeclarationCode(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{1,63}$");

    private static final String SHAPE =
            "upper-case with underscores, 2-64 characters, e.g. DIRECTOR_IN_COMPANY";

    public DeclarationCode {
        value = Identifiers.require(value, PATTERN, "DeclarationCode", SHAPE);
    }

    @Override
    public String toString() {
        return value;
    }
}
