package com.thiru.wealthlens.taxengine.rules.timeline;

import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
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
 * @param pipelineNodes the exec node catalogue, each node's file verbatim. Nothing reads its fields
 *     yet; the item that generates node ids from it gives it a type (D-053)
 * @param coverage the coverage register ({@code content/coverage.json}) verbatim, where the timeline
 *     has one. It describes the whole timeline rather than a year, so no year's pack is compiled from it
 *     (D-059)
 */
public record Timeline(
        Map<RuleId, RuleRow> rules,
        Map<RegimeCode, RegimeSpec> regimes,
        Map<RuleId, String> ruleSources,
        Map<RegimeCode, String> regimeSources,
        Map<NodeId, String> pipelineNodes,
        Optional<String> coverage) {

    public Timeline {
        rules = Map.copyOf(rules);
        regimes = Map.copyOf(regimes);
        ruleSources = Map.copyOf(ruleSources);
        regimeSources = Map.copyOf(regimeSources);
        pipelineNodes = Map.copyOf(pipelineNodes);
        coverage = Objects.requireNonNull(coverage, "coverage: Optional.empty() where the timeline has none");
    }
}
