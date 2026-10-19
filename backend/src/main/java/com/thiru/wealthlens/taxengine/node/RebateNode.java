package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.TaxByBasis;
import com.thiru.wealthlens.taxengine.rules.condition.Condition;
import com.thiru.wealthlens.taxengine.rules.condition.ConditionContext;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.relief.ReliefContext;
import com.thiru.wealthlens.taxengine.rules.relief.ReliefResult;
import com.thiru.wealthlens.taxengine.rules.relief.RuleRelief;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Tax after the rebates, each taken from its own pack row and only in the regime that row names
 * ({@code DR 06} G3.1, D-074).
 *
 * <p>Each rebate is computed on the whole tax, as s.156(2)(b) reads "the income-tax payable on such
 * total income"; a rebate its row limits to slab-rate tax (s.156(3)) is then held to
 * {@link TaxByBasis#atSlabRates}, so ₹12,00,000 that is mostly capital gains keeps its special-rate
 * tax ({@code DR 13} T13-1). The rebates come off the slab-rate tax only.
 *
 * <p>Every rebate row that applies in the running regime leaves a line, a nil one included, with its
 * reason in {@link TraceLine#limitedBy} — an unavailable relief is explained, never absent (P5). A
 * row for another regime leaves no line: s.156(2) does not exist for the First Schedule's rates
 * ({@code DR 08} T1-O2).
 */
public final class RebateNode implements ComputationNode {

    @Override
    public NodeId produces() {
        return NodeIds.TAX_AFTERREBATE;
    }

    /** The total income as well as the tax, because every rebate's ceiling is a total-income test. */
    @Override
    public Set<NodeId> requires() {
        return Set.of(NodeIds.TAX_BYBASIS, NodeIds.ROUND_TOTALINCOME);
    }

    @Override
    public Set<RuleSetId> ruleSets() {
        return Set.of(RuleSetIds.RS_REBATE);
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        Money totalIncome = ctx.get(NodeIds.ROUND_TOTALINCOME, MoneyValue.class).amount();
        TaxByBasis tax = ctx.get(NodeIds.TAX_BYBASIS, TaxByBasis.class);
        ConditionContext conditions = ctx.conditions();

        List<TraceLine> lines = new ArrayList<>();
        Money rebated = Money.ZERO;
        // In the set's order: the default regime's two, then the First Schedule's (D-252)
        for (RuleRow row : ctx.rulesOf(RuleSetIds.RS_REBATE)) {
            RuleId id = row.id();
            RuleVersion version = row.versionAt(ctx.inputs().temporal());
            if (!ctx.inRegime(id, version).applies()) {
                continue;
            }
            ReliefResult granted = grant(id, version, totalIncome, tax, conditions);
            rebated = rebated.plus(granted.amount());
            lines.add(TraceLine.of(lines.size() + 1, produces(), id, StepKind.RELIEF,
                            MoneyValue.of(tax.asMoney()), MoneyValue.of(granted.amount()), version.provenance())
                    .withLimitedBy(granted.reason()));
        }
        if (rebated.isGreaterThan(tax.atSlabRates())) {
            throw new IllegalStateException("Rebates of " + rebated + " under regime " + ctx.regime().code()
                    + " exceed the tax at slab rates " + tax.atSlabRates() + "; the pack's rebate rows overlap");
        }
        return new NodeResult(new TaxByBasis(tax.atSlabRates().minus(rebated), tax.atSpecialRates()), lines);
    }

    private static ReliefResult grant(
            RuleId id, RuleVersion version, Money totalIncome, TaxByBasis tax, ConditionContext conditions) {
        Optional<Condition> unmet = version.conditions().stream()
                .filter(condition -> !condition.holds(conditions))
                .findFirst();
        if (unmet.isPresent()) {
            return new ReliefResult(Money.ZERO, "not met: " + unmet.get().describe());
        }
        RuleRelief relief = version.relief().orElseThrow(() -> new IllegalStateException(
                "Rebate rule " + id + " carries no relief"));
        ReliefResult onTheTax = relief.shape().apply(new ReliefContext(totalIncome, tax.asMoney(), Optional.empty()));
        if (relief.limitedToSlabRateTax()) {
            return onTheTax.amount().isGreaterThan(tax.atSlabRates())
                    ? new ReliefResult(tax.atSlabRates(), "limited to the tax at slab rates, " + tax.atSlabRates())
                    : onTheTax;
        }
        if (onTheTax.amount().isGreaterThan(Money.ZERO) && tax.totalAtSpecialRates().isGreaterThan(Money.ZERO)) {
            throw new IllegalStateException("Rebate rule " + id + " is not limited to slab-rate tax, and there is"
                    + " special-rate tax of " + tax.totalAtSpecialRates() + "; how the two meet is unsourced (W-089)");
        }
        return onTheTax;
    }
}
