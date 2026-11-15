package com.thiru.wealthlens.taxengine.rules.catalogue;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Map;

/**
 * A claim catalogue entry as a year's pack carries it (W-118 C2a): whole, so the engine can serve it without the
 * timeline — its section's plain name and summary beside it, and the year's figure for each {@code RULE-ID.field} its
 * texts name.
 *
 * @param entry the entry, its texts holding placeholders
 * @param plainName the section's plain name
 * @param summary the section's summary
 * @param figures each placeholder, as {@code RULE-ID.field}, with its figure for the year
 */
public record CompiledClaim(ClaimCatalogueEntry entry, String plainName, String summary, Map<String, Money> figures) {

    public CompiledClaim {
        if (entry == null || plainName == null || plainName.isBlank() || summary == null || summary.isBlank()
                || figures == null) {
            throw new IllegalArgumentException("A compiled claim needs its entry, its section's plain name and summary,"
                    + " and its figures (an empty map for none)");
        }
        figures = Map.copyOf(figures);
    }
}
