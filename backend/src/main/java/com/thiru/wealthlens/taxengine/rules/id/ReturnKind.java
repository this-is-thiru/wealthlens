package com.thiru.wealthlens.taxengine.rules.id;

import java.util.regex.Pattern;

/**
 * The kind of return a form serves — the provision it is furnished under, such as an original or an updated return
 * (W-090b, D-521 C2a). An open code rather than an enum: which kinds exist, and which form serves which, is the
 * year's law (r.164(1) names s.263(1), s.268(1)(a) and s.280; r.165 the updated return), so the values are rows of
 * the year's forms. It lives here because the forms read it and a return's inputs will name it.
 */
public record ReturnKind(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{1,63}$");

    private static final String SHAPE = "upper-case with underscores, 2-64 characters, e.g. ORIGINAL";

    public ReturnKind {
        value = Identifiers.require(value, PATTERN, "ReturnKind", SHAPE);
    }

    @Override
    public String toString() {
        return value;
    }
}
