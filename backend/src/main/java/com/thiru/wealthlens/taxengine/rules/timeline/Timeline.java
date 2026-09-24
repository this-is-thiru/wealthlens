package com.thiru.wealthlens.taxengine.rules.timeline;

import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.regime.RegimeSpec;
import java.util.Map;

/**
 * The law authored once, across every year (D-034): the source each tax year's rule pack is compiled
 * from, so that a regime living ten years is one file rather than ten copies free to drift.
 *
 * @param rules every rule with all its versions, by id
 * @param regimes every regime, by code
 * @param pipelineNodes the exec node catalogue, each node's file verbatim. Nothing reads its fields
 *     yet; the item that generates node ids from it gives it a type (D-053)
 */
public record Timeline(
        Map<RuleId, RuleRow> rules, Map<RegimeCode, RegimeSpec> regimes, Map<NodeId, String> pipelineNodes) {

    public Timeline {
        rules = Map.copyOf(rules);
        regimes = Map.copyOf(regimes);
        pipelineNodes = Map.copyOf(pipelineNodes);
    }
}
