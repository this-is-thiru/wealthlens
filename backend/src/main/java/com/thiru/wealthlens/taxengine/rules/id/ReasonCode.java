package com.thiru.wealthlens.taxengine.rules.id;

import java.util.regex.Pattern;

/**
 * Why a trace line's figure is what it is, as a code the engine publishes and the pack's content puts into words
 * (F37.3; W-327a, D-678), such as {@code REGIME_DISALLOWS}.
 */
public record ReasonCode(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{1,63}$");

    private static final String SHAPE = "upper-case with underscores, 2-64 characters, e.g. REGIME_DISALLOWS";

    public ReasonCode {
        value = Identifiers.require(value, PATTERN, "ReasonCode", SHAPE);
    }

    @Override
    public String toString() {
        return value;
    }
}
