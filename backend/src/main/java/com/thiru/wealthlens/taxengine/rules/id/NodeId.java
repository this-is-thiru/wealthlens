package com.thiru.wealthlens.taxengine.rules.id;

import java.util.regex.Pattern;

/**
 * The name of one step in the computation graph, such as {@code salary.gross}.
 *
 * <p>Values flow between nodes by {@code NodeId}, never by field reference. A node declares what it
 * requires and what it produces, and the evaluation order is derived from those edges rather than
 * hand-maintained — so adding a step is a new class and a pack entry, not an insertion into a
 * numbered list where getting the position wrong yields a plausible wrong number.
 */
public record NodeId(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[a-z][a-z0-9.]{2,63}$");

    private static final String SHAPE =
            "lower-case, dot-separated, 3-64 characters, e.g. salary.gross";

    public NodeId {
        value = Identifiers.require(value, PATTERN, "NodeId", SHAPE);
    }

    @Override
    public String toString() {
        return value;
    }
}
