package com.thiru.wealthlens.taxengine.pipeline;

import java.util.regex.Pattern;

/**
 * Names a rate that is not the slab ladder — short-term capital gains at 20%, long-term at 12.5%,
 * lottery winnings at 30%.
 *
 * <p>These are kept apart from slab tax because several rules read one and not the other. The rebate
 * is the sharpest case: {@code 13-edge-cases.md} R-5 shows that computing it on total tax gives a nil
 * bill to someone with &#8377;12,00,000 of mostly capital gains, who owes it.
 *
 * @param value e.g. {@code "LTCG_198"}
 */
public record SpecialRateId(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{1,63}$");

    public SpecialRateId {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "A SpecialRateId must be upper-case with underscores, 2-64 characters,"
                            + " e.g. LTCG_198, but was \"" + value + "\"");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
