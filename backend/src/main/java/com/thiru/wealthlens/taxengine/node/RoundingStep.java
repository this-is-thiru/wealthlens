package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.NodeValue;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.rounding.RuleRounding;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.List;

/**
 * One of the two rounding points s.516 names: read an amount, round it as the row states, and say so
 * in one line (D-076). Shared by {@link RoundTotalIncomeNode} and {@link RoundNetPayableNode}, which
 * differ only in what they read and which row they cite.
 *
 * <p>The row states the step and the round-up point (D-316); this applies whichever it states. What it reads is summed:
 * the amount payable is the balance with the interest and fees on it (s.516 / s.288B, "any amount payable"; D-446 C5a).
 */
record RoundingStep(NodeId produces, List<NodeId> reads, RuleSetId ruleSet) {

    RoundingStep {
        reads = List.copyOf(reads);
    }

    NodeResult apply(NodeContext ctx) {
        Money amount = reads.stream().map(node -> ctx.get(node, NodeValue.class).asMoney()).reduce(Money.ZERO, Money::plus);
        RuleRow row = ctx.ruleOf(ruleSet);
        RuleId rule = row.id();
        RuleVersion version = row.versionAt(ctx.inputs().temporal());
        RuleRounding rounding = version.rounding().orElseThrow(() -> new IllegalStateException(
                "Rounding rule " + rule + " states no rounding"));
        MoneyValue rounded = MoneyValue.of(amount.roundToMultiple(rounding.toNearest(), rounding.roundsUpFrom()));
        return NodeResult.of(rounded, TraceLine.of(
                1, produces, rule, StepKind.ROUNDING, MoneyValue.of(amount), rounded, version.provenance()));
    }
}
