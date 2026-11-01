package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.FilingFacts;
import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Tax already paid for the year, credited against the bill: at source, in advance and on the return (2025 s.266(2)(a),
 * (b), s.390(5), s.410; 1961 s.140A(1), s.199(1), s.219; W-089b, D-436). A payment's date is not read: whenever it was
 * made it is tax already paid, so the total does not turn on it; interest on a late one is {@code W-112}'s.
 */
public final class CreditsNode implements ComputationNode {

    @Override
    public NodeId produces() {
        return NodeIds.CREDITS;
    }

    @Override
    public Set<NodeId> requires() {
        return Set.of();
    }

    @Override
    public Set<RuleSetId> ruleSets() {
        return Set.of(RuleSetIds.RS_CREDIT_TAX_PAID);
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        RuleId rule = ctx.ruleOf(RuleSetIds.RS_CREDIT_TAX_PAID).id();
        FilingFacts filing = ctx.inputs().filing();
        List<TraceLine> lines = new ArrayList<>();
        Money total = credit(ctx, rule, "tax at source", filing.taxAtSource(), Money.ZERO, lines);
        total = credit(ctx, rule, "advance tax", filing.totalAdvanceTaxPaid(), total, lines);
        total = credit(ctx, rule, "self-assessment tax", filing.totalSelfAssessmentPaid(), total, lines);
        return new NodeResult(MoneyValue.of(total), lines, List.of());
    }

    /** The running total with {@code amount} added, and a line for it. */
    private static Money credit(NodeContext ctx, RuleId rule, String kind, Money amount, Money runningTotal,
            List<TraceLine> lines) {
        lines.add(ctx.traceLine(lines.size() + 1, rule, StepKind.MEASURE, amount, amount).withLimitedBy(kind));
        return runningTotal.plus(amount);
    }
}
