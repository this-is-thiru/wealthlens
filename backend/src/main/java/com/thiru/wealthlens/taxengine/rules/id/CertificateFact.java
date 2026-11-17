package com.thiru.wealthlens.taxengine.rules.id;

import java.util.regex.Pattern;

/**
 * What a certificate's line states, named so a check can find it in any annexure or form without its path (W-128c, D-750
 * C1a): {@code STANDARD_DEDUCTION}, {@code CESS}. An open code: a new one is data, not code.
 */
public record CertificateFact(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{1,31}$");

    private static final String SHAPE = "upper-case with underscores, 2-32 characters, e.g. STANDARD_DEDUCTION";

    public CertificateFact {
        value = Identifiers.require(value, PATTERN, "CertificateFact", SHAPE);
    }

    @Override
    public String toString() {
        return value;
    }
}
