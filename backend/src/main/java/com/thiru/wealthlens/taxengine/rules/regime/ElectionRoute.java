package com.thiru.wealthlens.taxengine.rules.regime;

import java.util.regex.Pattern;

/**
 * A named way into a regime, where a regime has more than one.
 *
 * <p>s.202(4) has two: paragraph (a) for a person with business or professional income, and
 * paragraph (b) for everyone else. They are the same regime reached by different routes with
 * different binding, which is why a route is not the same thing as a {@code RegimeCode}.
 *
 * <p>{@link AfterWithdrawal.IneligibleUnless} names the route that reopens when a revival condition
 * later holds — the s.202(4)(a)(iv) carve-out.
 *
 * @param value e.g. {@code "S202_4_B"}
 */
public record ElectionRoute(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{1,63}$");

    public ElectionRoute {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "An ElectionRoute must be upper-case with underscores, 2-64 characters,"
                            + " e.g. S202_4_B, but was \"" + value + "\"");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
