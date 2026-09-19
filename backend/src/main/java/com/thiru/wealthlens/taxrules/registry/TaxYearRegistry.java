package com.thiru.wealthlens.taxrules.registry;

import com.thiru.wealthlens.taxrules.id.TaxYearId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Which tax years exist, and under what law. The entry point to every computation.
 *
 * <p>{@code D-REGISTRY}: <b>no computation begins without resolving a registry entry.</b> That is
 * not a convention but the mechanism that makes observation O-4 impossible. In the placeholder,
 * {@code TaxYearRegistryEntity} modelled the start date, end date, governing Act, governing Rules
 * and default regime — and was never read or written. A model with no consumer drifts from reality
 * silently, because nothing depends on it being right.
 *
 * <p>Here, nothing works without it: a year absent from this registry cannot be computed, and the
 * refusal names the years that can be.
 */
public record TaxYearRegistry(Map<TaxYearId, TaxYearRegistryEntry> entries) {

    public TaxYearRegistry {
        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException(
                    "A registry with no years supports no computation at all");
        }
        Map<TaxYearId, TaxYearRegistryEntry> copied = new LinkedHashMap<>(entries);
        for (Map.Entry<TaxYearId, TaxYearRegistryEntry> entry : copied.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().taxYear())) {
                throw new IllegalArgumentException(
                        "Entry filed under " + entry.getKey().label() + " describes "
                                + entry.getValue().taxYear().label());
            }
        }
        entries = Map.copyOf(copied);
    }

    /** The years this registry supports. */
    public Set<TaxYearId> supportedYears() {
        return entries.keySet();
    }

    /** Whether a year can be computed at all. */
    public boolean supports(TaxYearId taxYear) {
        return entries.containsKey(taxYear);
    }

    /**
     * The entry for a year.
     *
     * @throws UnsupportedTaxYearException naming the supported years — golden case T9-2
     */
    public TaxYearRegistryEntry entryFor(TaxYearId taxYear) {
        TaxYearRegistryEntry entry = entries.get(taxYear);
        if (entry == null) {
            throw new UnsupportedTaxYearException(taxYear, entries.keySet());
        }
        return entry;
    }
}
