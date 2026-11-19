package com.thiru.wealthlens.taxengine.rules.id;

import java.util.regex.Pattern;

/**
 * One manner in which a return may be furnished — electronically under a digital signature, on paper (W-090b,
 * D-521 C2a). An open code rather than an enum: the manners are the year's law (r.164(12)'s Table), so each is named
 * by a form's row. The verification sent after an electronic upload is one of them, not a form (D-516 C4a).
 */
public record FurnishingManner(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{1,63}$");

    private static final String SHAPE = "upper-case with underscores, 2-64 characters, e.g. ELECTRONIC_EVC";

    public FurnishingManner {
        value = Identifiers.require(value, PATTERN, "FurnishingManner", SHAPE);
    }

    @Override
    public String toString() {
        return value;
    }
}
