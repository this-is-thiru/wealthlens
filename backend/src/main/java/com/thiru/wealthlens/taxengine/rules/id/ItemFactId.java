package com.thiru.wealthlens.taxengine.rules.id;

import java.util.regex.Pattern;

/**
 * Names a fact about the one item a node is computing — a car's engine, an accommodation's provider, the entry a
 * retirement payment is made under — such as {@code vehicle.engine} (W-088m2, D-253).
 *
 * <p>A rule row says which item it is for by a condition on these facts, so which row a car or a payment takes is the
 * pack's to say rather than a Java map's.
 */
public record ItemFactId(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[a-z][a-z0-9.]{2,63}$");

    private static final String SHAPE = "lower-case, dot-separated, 3-64 characters, e.g. vehicle.engine";

    public ItemFactId {
        value = Identifiers.require(value, PATTERN, "ItemFactId", SHAPE);
    }

    @Override
    public String toString() {
        return value;
    }
}
