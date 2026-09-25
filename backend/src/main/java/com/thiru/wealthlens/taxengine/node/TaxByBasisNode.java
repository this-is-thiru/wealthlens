package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.TaxByBasis;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.cap.SlabLadder;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.Set;

/**
 * Tax at slab rates on the rounded total income, from the ladder the regime names
 * ({@code DR 16} S2 step 5, D-071).
 *
 * <p>The ladder is the regime's {@code rateTable}, not a rule fixed here, so a regime that
 * arrives as a pack row brings its own rates with no change to this class. Special-rate income is
 * not this node's yet; the result is {@link TaxByBasis#atSlabRatesOnly}. The figure is not rounded:
 * rounding happens in exactly two places, neither of them a band ({@code RATE-ROUND-003}).
 */
public final class TaxByBasisNode implements ComputationNode {

    @Override
    public NodeId produces() {
        return NodeIds.TAX_BYBASIS;
    }

    @Override
    public Set<NodeId> requires() {
        return Set.of(NodeIds.ROUND_TOTALINCOME);
    }

    /** Every ladder a shipped regime names; a regime naming another is refused by {@link NodeContext#rule}. */
    @Override
    public Set<RuleId> rules() {
        return Set.of(RuleIds.RATE_SLAB_NEW_TAX_REGIME, RuleIds.RATE_SLAB_FIRST_SCHEDULE);
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        Money totalIncome = ctx.get(NodeIds.ROUND_TOTALINCOME, MoneyValue.class).amount();
        RuleRow row = ctx.rule(ctx.regime().rateTable());
        RuleVersion version = row.versionAt(ctx.inputs().temporal());
        if (!(version.cap() instanceof SlabLadder ladder)) {
            throw new IllegalStateException("Rate table " + row.id() + " for regime " + ctx.regime().code()
                    + " is a " + version.cap().shapeName() + ", not a slab ladder");
        }
        Money tax = ladder.apply(CapContext.of(ladder.base(), totalIncome)).value();
        TaxByBasis value = TaxByBasis.atSlabRatesOnly(tax);
        return NodeResult.of(value, TraceLine.of(
                1, produces(), row.id(), StepKind.TAX, MoneyValue.of(totalIncome), value, version.provenance()));
    }
}
