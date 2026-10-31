package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.condition.Condition;
import com.thiru.wealthlens.taxengine.rules.condition.ConditionContext;
import com.thiru.wealthlens.taxengine.rules.condition.ItemFacts;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;

/**
 * Whether a row reaches one item: the conditions of its governing version that are about the item hold for it (W-154b,
 * W-155b). The row's other conditions are the person's, tested where the row is applied, so a row can say which items
 * it is for without the node restating it — the one test the specified-employee gate, the retirement bars and the
 * other-sources rows share (W-155d).
 */
final class RowReach {

    private RowReach() {
    }

    static boolean holdsFor(NodeContext ctx, RuleRow row, ItemFacts item) {
        ConditionContext about = ctx.conditions().about(item);
        return TemporallyApportionedCap.governingVersion(row, ctx.inputs().temporal()).conditions().stream()
                .filter(Condition::isAboutItem)
                .allMatch(condition -> condition.holds(about));
    }
}
