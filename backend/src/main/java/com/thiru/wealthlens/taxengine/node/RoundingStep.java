package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.rounding.RuleRounding;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;

/**
 * One of the two rounding points s.516 names: read an amount, round it as the row states, and say so
 * in one line (D-076). Shared by {@link RoundTotalIncomeNode} and {@link RoundNetPayableNode}, which
 * differ only in what they read and which row they cite.
 *
 * <p>The row states the step and the round-up point (D-316); this applies whichever it states.
 */
record RoundingStep(NodeId produces, NodeId reads, RuleSetId ruleSet) {

    NodeResult apply(NodeContext ctx) {
        Money amount = ctx.get(reads, MoneyValue.class).amount();
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
