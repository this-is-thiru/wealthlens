package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.rounding.RuleRounding;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;

/**
 * One of the two rounding points s.516 names: read an amount, round it as the row states, and say so
 * in one line (D-076). Shared by {@link RoundTotalIncomeNode} and {@link RoundNetPayableNode}, which
 * differ only in what they read and which row they cite.
 *
 * <p>The row states the step; the only method implemented is s.516's ₹10, so any other step is refused
 * rather than rounded to ten under a row that says otherwise.
 */
record RoundingStep(NodeId produces, NodeId reads, RuleId rule) {

    private static final Money SECTION_516_STEP = Money.TEN;

    NodeResult apply(NodeContext ctx) {
        Money amount = ctx.get(reads, MoneyValue.class).amount();
        RuleVersion version = ctx.rule(rule).versionAt(ctx.inputs().temporal());
        RuleRounding rounding = version.rounding().orElseThrow(() -> new IllegalStateException(
                "Rounding rule " + rule + " states no rounding"));
        if (!rounding.toNearest().equals(SECTION_516_STEP)) {
            throw new IllegalStateException("Rounding rule " + rule + " rounds to " + rounding.toNearest()
                    + "; only s.516's step of " + SECTION_516_STEP + " is implemented");
        }
        MoneyValue rounded = MoneyValue.of(amount.roundToNearestTenPerSection516());
        return NodeResult.of(rounded, TraceLine.of(
                1, produces, rule, StepKind.ROUNDING, MoneyValue.of(amount), rounded, version.provenance()));
    }
}
