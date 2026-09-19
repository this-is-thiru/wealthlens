package com.thiru.wealthlens.taxrules.id;

import java.util.regex.Pattern;

/**
 * Shared validation for the identifier records in this package.
 *
 * <p>Seven records apply the same three checks — not null, matches a shape, and says what the shape
 * was when it does not. Repeating that seven times is how six of them end up with a helpful message
 * and one does not.
 */
final class Identifiers {

    private Identifiers() {
    }

    /**
     * @param shape a human description of the pattern, shown on failure — a regex in an error
     *     message tells the reader what was rejected but not what to write instead
     */
    static String require(String value, Pattern pattern, String typeName, String shape) {
        if (value == null) {
            throw new IllegalArgumentException(typeName + " may not be null");
        }
        if (!pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    typeName + " must be " + shape + ", but was \"" + value + "\"");
        }
        return value;
    }
}
