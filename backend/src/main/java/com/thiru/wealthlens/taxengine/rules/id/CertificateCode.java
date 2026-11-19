package com.thiru.wealthlens.taxengine.rules.id;

import java.util.regex.Pattern;

/**
 * A certificate an employer or bank issues, named as the form is: {@code FORM_130} (W-126a, D-729). The name a document's
 * type has in {@code taxplanning}, so the two meet by name.
 */
public record CertificateCode(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{1,15}$");

    private static final String SHAPE = "upper-case with underscores, 2-16 characters, e.g. FORM_130";

    public CertificateCode {
        value = Identifiers.require(value, PATTERN, "CertificateCode", SHAPE);
    }

    @Override
    public String toString() {
        return value;
    }
}
