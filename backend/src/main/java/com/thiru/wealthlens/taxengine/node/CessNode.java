package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.TaxByBasis;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.cap.PercentOfBase;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.Set;

/**
 * The Health and Education Cess — a percentage "of such income-tax and surcharge" (FA 2026 s.3(15)),
 * charged on the tax after rebate plus the surcharge ({@code REBATE-ORDER-001}, D-079, D-086).
 *
 * <p>The order is kept by what the node declares: it reads {@code tax.afterrebate} and
 * {@code tax.surcharge} and nothing earlier, so it cannot be charged on the tax before the rebate. The
 * whole income-tax is the base, slab-rate and special-rate alike; s.3(15) does not split it.
 */
public final class CessNode implements ComputationNode {

    @Override
    public NodeId produces() {
        return NodeIds.TAX_CESS;
    }

    @Override
    public Set<NodeId> requires() {
        return Set.of(NodeIds.TAX_AFTERREBATE, NodeIds.TAX_SURCHARGE);
    }

    @Override
    public Set<RuleId> rules() {
        return Set.of(RuleIds.RATE_HEALTH_EDUCATION_CESS);
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        Money taxAndSurcharge = ctx.get(NodeIds.TAX_AFTERREBATE, TaxByBasis.class)
                .asMoney()
                .plus(ctx.get(NodeIds.TAX_SURCHARGE, MoneyValue.class).amount());
        RuleVersion version = ctx.rule(RuleIds.RATE_HEALTH_EDUCATION_CESS).versionAt(ctx.inputs().temporal());
        if (!(version.cap() instanceof PercentOfBase rate)) {
            throw new IllegalStateException("Cess rule " + RuleIds.RATE_HEALTH_EDUCATION_CESS + " is "
                    + (version.cap() == null ? "not a rate" : "a " + version.cap().shapeName())
                    + ", not a percentage of the tax");
        }
        Money cess = TemporallyApportionedCap.apply(ctx.rule(RuleIds.RATE_HEALTH_EDUCATION_CESS),
                        ctx.inputs().temporal(), CapContext.of(rate.base(), taxAndSurcharge))
                .value();
        return NodeResult.of(MoneyValue.of(cess), TraceLine.of(1, produces(), RuleIds.RATE_HEALTH_EDUCATION_CESS,
                StepKind.TAX, MoneyValue.of(taxAndSurcharge), MoneyValue.of(cess), version.provenance()));
    }
}
