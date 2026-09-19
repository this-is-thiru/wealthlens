package com.thiru.wealthlens.taxrules.registry;

import com.thiru.wealthlens.taxrules.id.TaxYearId;
import com.thiru.wealthlens.taxrules.pack.RulePackLayer;
import com.thiru.wealthlens.taxrules.pack.RulePackRef;
import java.util.EnumMap;
import java.util.Map;

/**
 * One supported tax year: the law that governs it, and the pack that encodes that law.
 *
 * <h2>Three instruments, not one</h2>
 *
 * <p>{@code YR-ACT-002}/{@code -003}/{@code -004} require the Act, the Finance Act <b>and</b> the
 * Rules. The placeholder carried Act and Rules only, which sounds like a small omission and is not:
 * the Finance Act is where the surcharge ladders and the marginal-relief formulae actually live
 * ({@code RATE-SUR-*}, {@code RATE-SUR-MR-001}). Without it the provenance of the highest-risk rules
 * in the product cannot be rendered at all.
 *
 * <h2>No default regime here</h2>
 *
 * <p>The pack answers that. Invariant I5 guarantees exactly one default regime per
 * {@code (taxYear, PersonType)}, so a field here would be a second source of truth able to disagree
 * with the first — and a single field could not express a per-person-type answer anyway.
 *
 * @param taxYear the year
 * @param instruments the three instruments governing it
 * @param packLayers the pack's per-layer versions and checksums — the identity a computation receipt
 *     records, so a stored computation can be reproduced under exactly the rules it was made under
 */
public record TaxYearRegistryEntry(
        TaxYearId taxYear,
        GoverningInstruments instruments,
        Map<RulePackLayer, RulePackRef> packLayers) {

    public TaxYearRegistryEntry {
        if (taxYear == null) {
            throw new IllegalArgumentException("A registry entry must name its tax year");
        }
        if (instruments == null) {
            throw new IllegalArgumentException(
                    taxYear + " must name the Act, the Finance Act and the Rules that govern it");
        }
        if (packLayers == null) {
            throw new IllegalArgumentException(taxYear + " must name the pack that encodes its law");
        }
        Map<RulePackLayer, RulePackRef> copied = new EnumMap<>(RulePackLayer.class);
        copied.putAll(packLayers);
        for (RulePackLayer layer : RulePackLayer.values()) {
            if (!copied.containsKey(layer)) {
                throw new IllegalArgumentException(
                        taxYear + " names no pack version for layer " + layer);
            }
        }
        packLayers = Map.copyOf(copied);
    }
}
