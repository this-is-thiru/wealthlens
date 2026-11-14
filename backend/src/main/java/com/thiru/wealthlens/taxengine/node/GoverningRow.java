package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;

/**
 * The row a step applies, as its rule set chose it, with the version that governs the year — what a trace line names
 * and cites (D-252). Carried together so a step never names a row its set did not choose.
 */
record GoverningRow(RuleId id, RuleVersion version) {

    /** The row {@code set} gives this computation, and its governing version. */
    static GoverningRow of(NodeContext ctx, RuleSetId set) {
        RuleRow row = ctx.ruleOf(set);
        return new GoverningRow(row.id(), TemporallyApportionedCap.governingVersion(row, ctx.inputs().temporal()));
    }
}
