package com.thiru.wealthlens.taxengine.rules.pack;

import com.thiru.wealthlens.taxengine.rules.component.ComponentDefinition;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.regime.ConditionSetId;
import com.thiru.wealthlens.taxengine.rules.regime.DisallowanceSetId;
import com.thiru.wealthlens.taxengine.rules.regime.RegimeSpec;
import com.thiru.wealthlens.taxengine.rules.registry.GoverningInstruments;
import com.thiru.wealthlens.taxengine.rules.registry.PublicationStatus;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * One tax year's law, as data.
 *
 * <p>Immutable and checksummed. Every computation reads a pack and nothing else, which is what makes
 * a stored computation reproducible years later: the pack version in its receipt identifies exactly
 * the rules it was computed under, including amendments made after the fact.
 *
 * <p>The four layers are versioned <b>separately</b> — see {@link RulePackLayer}. That is not
 * bookkeeping: it is what makes {@link #computationCacheKey()} stable across editorial changes.
 *
 * @param taxYear the year this pack governs
 * @param layers each layer's version and checksum; all four required
 * @param instruments the Acts and Rules this pack was read from
 * @param rules by id
 * @param components the component registry
 * @param regimes by code
 * @param disallowanceSets shared disallowance lists, by id — the s.205(1) pattern
 * @param conditionSets shared eligibility lists, by id — the s.205(2) pattern
 */
public record RulePack(
        TaxYearId taxYear,
        Map<RulePackLayer, RulePackRef> layers,
        GoverningInstruments instruments,
        Map<RuleId, RuleRow> rules,
        Map<ComponentCode, ComponentDefinition> components,
        Map<RegimeCode, RegimeSpec> regimes,
        Map<DisallowanceSetId, DisallowanceSet> disallowanceSets,
        Map<ConditionSetId, ConditionSet> conditionSets) {

    public RulePack {
        if (taxYear == null) {
            throw new IllegalArgumentException("A rule pack must name its tax year");
        }
        layers = everyLayerVersioned(taxYear, layers);

        if (instruments == null) {
            throw new IllegalArgumentException(taxYear + " must name its governing instruments");
        }
        rules = rules == null ? Map.of() : Map.copyOf(rules);
        components = components == null ? Map.of() : Map.copyOf(components);
        regimes = regimes == null ? Map.of() : Map.copyOf(regimes);
        disallowanceSets = disallowanceSets == null ? Map.of() : Map.copyOf(disallowanceSets);
        conditionSets = conditionSets == null ? Map.of() : Map.copyOf(conditionSets);

        requireRulesFiledUnderTheirOwnIds(taxYear, rules);
        requireOnlyPublishedVersions(taxYear, rules);
    }

    private static Map<RulePackLayer, RulePackRef> everyLayerVersioned(
            TaxYearId taxYear, Map<RulePackLayer, RulePackRef> layers) {
        if (layers == null) {
            throw new IllegalArgumentException("A rule pack must carry its layer versions");
        }
        Map<RulePackLayer, RulePackRef> copied = new EnumMap<>(RulePackLayer.class);
        copied.putAll(layers);
        for (RulePackLayer layer : RulePackLayer.values()) {
            RulePackRef ref = copied.get(layer);
            if (ref == null) {
                throw new IllegalArgumentException(
                        taxYear + " has no version for layer " + layer
                                + "; an unversioned layer cannot be verified or cached against");
            }
            if (ref.layer() != layer) {
                throw new IllegalArgumentException(
                        "Layer " + layer + " maps to a ref describing " + ref.layer());
            }
        }
        return Map.copyOf(copied);
    }

    /**
     * Only {@code PUBLISHED} versions enter a pack (D-036). A pack is compiled output, so any other
     * status here means the pack was built wrong; it is refused rather than filtered, because
     * filtering belongs to the compiler (D-052).
     */
    private static void requireOnlyPublishedVersions(TaxYearId taxYear, Map<RuleId, RuleRow> rules) {
        for (RuleRow row : rules.values()) {
            for (RuleVersion version : row.versions()) {
                if (version.publicationStatus() != PublicationStatus.PUBLISHED) {
                    throw new IllegalArgumentException(
                            taxYear + " rule " + row.id() + " holds a " + version.publicationStatus()
                                    + " version; only PUBLISHED versions may enter a rule pack");
                }
            }
        }
    }

    private static void requireRulesFiledUnderTheirOwnIds(TaxYearId taxYear, Map<RuleId, RuleRow> rules) {
        if (rules.isEmpty()) {
            throw new IllegalArgumentException(taxYear + " has no rules; it would compute nothing");
        }
        for (Map.Entry<RuleId, RuleRow> entry : rules.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().id())) {
                throw new IllegalArgumentException(
                        "Rule filed under " + entry.getKey() + " calls itself " + entry.getValue().id());
            }
        }
    }

    /**
     * The key a cached computation is stored under.
     *
     * <p>Covers {@link RulePackLayer#LAW} and {@link RulePackLayer#EXEC} and <b>nothing else</b>, so
     * that seven editorial revisions to the claim catalogue invalidate not one cached figure. That
     * is the whole return on layering the pack.
     *
     * <p>Readable rather than hashed, deliberately. A cache key in a log that says
     * {@code 2026-27|law=2026-27.law.3@sha256:… } lets someone diagnose a miss; an opaque digest
     * does not, and there is nothing secret here to compress away.
     */
    public String computationCacheKey() {
        return taxYear.label() + "|"
                + layers.values().stream()
                        .filter(ref -> ref.layer().affectsComputation())
                        .sorted(java.util.Comparator.comparing(ref -> ref.layer().name()))
                        .map(RulePackRef::cacheFragment)
                        .collect(Collectors.joining("|"));
    }

    /** The version of one layer, for a receipt or a support ticket. */
    public RulePackRef layer(RulePackLayer layer) {
        return layers.get(layer);
    }

    /**
     * A rule by id.
     *
     * @throws IllegalArgumentException if the pack has no such rule — a reference the validator
     *     should have rejected at load (invariant I2)
     */
    public RuleRow rule(RuleId id) {
        RuleRow row = rules.get(id);
        if (row == null) {
            throw new IllegalArgumentException(
                    "Pack " + taxYear + " has no rule " + id + "; it holds " + rules.size() + " rules");
        }
        return row;
    }
}
