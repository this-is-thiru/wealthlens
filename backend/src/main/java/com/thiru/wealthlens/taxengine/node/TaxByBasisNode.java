package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.TaxByBasis;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.cap.SlabLadder;
import com.thiru.wealthlens.taxengine.rules.condition.ConditionContext;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Tax at slab rates on the rounded total income, from the ladder the regime names
 * ({@code DR 16} S2 step 5, D-071).
 *
 * <p>The ladder is the regime's {@code rateTable}, not a rule fixed here, so a regime that
 * arrives as a pack row brings its own rates with no change to this class — unless a conditional
 * ladder, such as the First Schedule's for a resident of sixty or eighty, applies instead (D-072). Special-rate income is
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

    /**
     * Every slab ladder in the pack: each regime's {@code rateTable}, and the conditional ladders
     * that replace it for some assessees (D-072). A ladder not declared here is refused by
     * {@link NodeContext#rule}.
     */
    private static final Set<RuleId> LADDERS = Set.of(
            RuleIds.RATE_SLAB_NEW_TAX_REGIME,
            RuleIds.RATE_SLAB_FIRST_SCHEDULE,
            RuleIds.RATE_SLAB_FIRST_SCHEDULE_AGE_60TO79,
            RuleIds.RATE_SLAB_FIRST_SCHEDULE_AGE_80);

    @Override
    public Set<RuleId> rules() {
        return LADDERS;
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        Money totalIncome = ctx.get(NodeIds.ROUND_TOTALINCOME, MoneyValue.class).amount();
        RuleRow row = ladderFor(ctx);
        RuleVersion version = TemporallyApportionedCap.governingVersion(row, ctx.inputs().temporal());
        if (!(version.cap() instanceof SlabLadder ladder)) {
            throw new IllegalStateException("Rate table " + row.id() + " for regime " + ctx.regime().code()
                    + " is a " + version.cap().shapeName() + ", not a slab ladder");
        }
        Money tax = ladder.apply(CapContext.of(ladder.base(), totalIncome)).value();
        TaxByBasis value = TaxByBasis.atSlabRatesOnly(tax);
        return NodeResult.of(value, TraceLine.of(
                1, produces(), row.id(), StepKind.TAX, MoneyValue.of(totalIncome), value, version.provenance()));
    }

    /**
     * The regime's {@code rateTable}, unless a conditional ladder applies instead: one whose version
     * in force applies in this regime and whose conditions all hold. Two holding at once is a pack
     * defect, refused rather than resolved by picking one.
     */
    private static RuleRow ladderFor(NodeContext ctx) {
        RuleId table = ctx.regime().rateTable();
        ConditionContext conditions = ctx.conditions();
        List<RuleRow> holding = LADDERS.stream()
                .filter(id -> !id.equals(table))
                .map(ctx::rule)
                .filter(row -> replaces(TemporallyApportionedCap.governingVersion(row, ctx.inputs().temporal()),
                        ctx.regime().code(), conditions))
                .sorted(Comparator.comparing(row -> row.id().value()))
                .toList();
        if (holding.size() > 1) {
            throw new IllegalStateException("Slab ladders " + holding.stream().map(RuleRow::id).toList()
                    + " all apply under regime " + ctx.regime().code() + "; exactly one may replace " + table);
        }
        return holding.isEmpty() ? ctx.rule(table) : holding.getFirst();
    }

    private static boolean replaces(RuleVersion version, RegimeCode regime, ConditionContext conditions) {
        return version.regimes().appliesIn(regime)
                && !version.conditions().isEmpty()
                && version.conditions().stream().allMatch(condition -> condition.holds(conditions));
    }
}
