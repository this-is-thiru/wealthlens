package com.thiru.wealthlens.taxengine.rules.timeline;

import com.thiru.wealthlens.taxengine.rules.component.ComponentDefinition;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.regime.DisallowanceSetId;
import com.thiru.wealthlens.taxengine.rules.regime.RegimeSpec;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The law authored once, across every year (D-034): the source each tax year's rule pack is compiled
 * from, so that a regime living ten years is one file rather than ten copies free to drift.
 *
 * @param rules every rule with all its versions, by id
 * @param regimes every regime, by code
 * @param ruleSources each rule's file verbatim — what a year's pack is compiled from, since the parsed
 *     row cannot be written back
 * @param regimeSources each regime's file verbatim, for the same reason
 * @param disallowanceSetSources each disallowance set's file verbatim, by id — a set holds a version per span of
 *     years, and a year's pack is compiled from the one in force (D-165)
 * @param components every pay component, by code (D-089) — each arrives with the first rule that reads it
 * @param componentSources each component's file verbatim, for the reason {@code ruleSources} is kept
 * @param pipelineNodes the exec node catalogue, each node's file verbatim. Nothing reads its fields
 *     yet; the item that generates node ids from it gives it a type (D-053)
 * @param coverage the coverage register ({@code content/coverage.json}) verbatim, where the timeline
 *     has one. It describes the whole timeline rather than a year, so no year's pack is compiled from it
 *     (D-059)
 * @param sections the section catalogue ({@code content/sections/}), each entry's file verbatim by its
 *     key. Like the register it describes the whole timeline, so no year's pack is compiled from it
 *     (D-060)
 * @param acts each Act's section list ({@code content/acts/}), verbatim by the Act's code: what the
 *     coverage register must list exactly, for an Act that has one (D-066)
 */
public record Timeline(
        Map<RuleId, RuleRow> rules,
        Map<RegimeCode, RegimeSpec> regimes,
        Map<RuleId, String> ruleSources,
        Map<RegimeCode, String> regimeSources,
        Map<DisallowanceSetId, String> disallowanceSetSources,
        Map<ComponentCode, ComponentDefinition> components,
        Map<ComponentCode, String> componentSources,
        Map<NodeId, String> pipelineNodes,
        Optional<String> coverage,
        Map<String, String> sections,
        Map<String, String> acts) {

    public Timeline {
        rules = Map.copyOf(rules);
        regimes = Map.copyOf(regimes);
        ruleSources = Map.copyOf(ruleSources);
        regimeSources = Map.copyOf(regimeSources);
        disallowanceSetSources = Map.copyOf(disallowanceSetSources);
        components = Map.copyOf(components);
        componentSources = Map.copyOf(componentSources);
        pipelineNodes = Map.copyOf(pipelineNodes);
        coverage = Objects.requireNonNull(coverage, "coverage: Optional.empty() where the timeline has none");
        sections = Map.copyOf(sections);
        acts = Map.copyOf(acts);
    }
}
