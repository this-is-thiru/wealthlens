package com.thiru.wealthlens.taxengine.rules.id;

import java.util.regex.Pattern;

/**
 * The identity of a rule set, such as {@code RS-SAL-STANDARD-DEDUCTION}: the rows a node evaluates together, of
 * which the data says which applies (W-088m1, D-252).
 *
 * <p>A node names the set, never a row, so an alternative row — another regime's variant, a person-based case — is
 * added to the pack and reached with no change to Java.
 */
public record RuleSetId(String value) {

    /** {@code RS-} then one to seven hyphenated segments, so a set may carry the name of the row it began with. */
    private static final Pattern PATTERN = Pattern.compile("^RS(-[A-Z0-9]{1,12}){1,7}$");

    private static final String SHAPE = "RS- then 1-7 hyphenated segments, e.g. RS-SAL-STANDARD-DEDUCTION";

    public RuleSetId {
        value = Identifiers.require(value, PATTERN, "RuleSetId", SHAPE);
    }

    @Override
    public String toString() {
        return value;
    }
}
