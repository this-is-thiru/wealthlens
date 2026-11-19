package com.thiru.wealthlens.taxengine.rules.definition;

import com.thiru.wealthlens.taxengine.rules.id.ItemFactId;
import java.util.regex.Pattern;

/**
 * A part of the capital-gains head that losses are set off against and that is taxed on its own basis (D-243), such as
 * {@code LONG_TERM_LISTED_EQUITY}.
 *
 * <p>A code, not a closed set (D-309): which parts there are is {@code CG-PARTS}' to declare, by tax year, with each
 * part's term and whether a long-term loss may be set off against it ({@link CapitalGainsParts}); which bucket falls in
 * which part is {@code RS-CG-PART}'s, and which part a special rate charges the rate rows'. A part another provision
 * makes is new data; a row naming a part {@code CG-PARTS} does not declare fails the pack validation the build runs over
 * the shipped packs ({@code PackValidator}).
 *
 * @param value the code, upper-case with underscores
 */
public record CapitalGainsPart(String value) {

    /** The item fact a row names a part by — how a special-rate row says which part it charges (D-253). */
    public static final ItemFactId FACT = new ItemFactId("capitalgains.part");

    private static final Pattern PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{1,63}$");

    public CapitalGainsPart {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("CapitalGainsPart must be upper-case with underscores, 2-64 characters,"
                    + " e.g. LONG_TERM_LISTED_EQUITY, but was " + (value == null ? "null" : "\"" + value + "\""));
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
