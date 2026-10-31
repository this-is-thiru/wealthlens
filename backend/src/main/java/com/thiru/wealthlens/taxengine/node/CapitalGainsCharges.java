package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.CapitalGainsInput;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.Charge;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.condition.ItemFacts;
import com.thiru.wealthlens.taxengine.rules.definition.ChargeDeduction;
import com.thiru.wealthlens.taxengine.rules.definition.ChargeDeductionLimb;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinitions;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.ItemFactId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.List;
import java.util.Map;

/**
 * The gain of a capital-gains bucket: the consideration less the cost less the charges the law deducts (2025 s.72;
 * 1961 s.48; W-162a, D-375).
 *
 * <p>The Acts deduct transfer expenditure and the cost of acquisition, and bar securities transaction tax; which charge on
 * a contract note is which they do not say. So each charge is put to {@code RS-CG-CHARGE} by its side and code, and the
 * row's limb decides. A charge no row covers refuses the computation rather than being counted or dropped unseen: a
 * charge the catalogue learns later needs its row first. Every charge is a trace line naming its row.
 */
final class CapitalGainsCharges {

    static final RuleSetId DEDUCTIONS = RuleSetIds.RS_CG_CHARGE;

    private static final ItemFactId SIDE = new ItemFactId("charge.side");

    private static final ItemFactId CODE = new ItemFactId("charge.code");

    private CapitalGainsCharges() {
    }

    /** The bucket's gain, each charge's line added to {@code lines}. */
    static Money gainOf(NodeContext ctx, CapitalGainsInput.Bucket bucket, List<TraceLine> lines) {
        Money gain = bucket.consideration().minus(bucket.cost());
        for (Charge charge : bucket.charges()) {
            gain = gain.minus(deducted(ctx, bucket, charge, lines));
        }
        return gain;
    }

    private static Money deducted(NodeContext ctx, CapitalGainsInput.Bucket bucket, Charge charge,
            List<TraceLine> lines) {
        RuleRow row = ctx.optionalRuleOf(DEDUCTIONS, new ItemFacts(Map.of(SIDE, charge.side().name(), CODE,
                charge.code()), Map.of(), Map.of()))
                .orElseThrow(() -> new IllegalArgumentException("The charge " + charge.code() + " on the "
                        + charge.side() + " of capital gains (" + bucket.facts() + ") falls in no row of "
                        + DEDUCTIONS + " for " + ctx.pack().taxYear().label()
                        + ", so whether it reduces the gain is unknown"));
        ChargeDeductionLimb limb = RuleDefinitions.require(TemporallyApportionedCap.governingVersion(row,
                ctx.inputs().temporal()).definition(), ChargeDeduction.class, row.id()).limb();
        Money deducted = limb.deducts() ? charge.amount() : Money.ZERO;
        lines.add(ctx.traceLine(lines.size() + 1, row.id(), StepKind.MEASURE, charge.amount(), deducted)
                .withLimitedBy(charge.code() + " on the " + charge.side() + ": " + limb));
        return deducted;
    }
}
