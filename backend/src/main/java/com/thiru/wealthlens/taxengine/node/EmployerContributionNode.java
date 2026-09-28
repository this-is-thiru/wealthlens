package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.EmployerFundContribution;
import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapResult;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.List;
import java.util.Set;

/**
 * The employer's retirement contributions that are salary: their aggregate, across every employment, beyond the
 * limit in a tax year (S2 s.17(1)(h); 1961 s.17(2)(vii); {@code DR 01} PF-AGGCAP-001; D-126).
 *
 * <p>The contributions to a recognised provident fund, the notified pension scheme and an approved superannuation fund
 * are added up; only the excess is a perquisite, for every employee and in every regime, and gross salary adds it on
 * a line of its own. The accretion on the excess and the recognised fund's own charges are further items (W-073b–c);
 * the employer-NPS deduction is Chapter VIII's and is not netted here.
 */
public final class EmployerContributionNode implements ComputationNode {

    private static final BaseId CONTRIBUTIONS = new BaseId("CONTRIBUTIONS");

    @Override
    public NodeId produces() {
        return NodeIds.EMPLOYER_CONTRIB_CHARGES;
    }

    @Override
    public Set<NodeId> requires() {
        return Set.of();
    }

    @Override
    public Set<RuleId> rules() {
        return Set.of(RuleIds.PERQ_EMPLOYER_CONTRIBUTION);
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        List<EmployerFundContribution> contributions = ctx.inputs().entered().employerContributions();
        if (contributions.isEmpty()) {
            return NodeResult.untraced(MoneyValue.of(Money.ZERO));
        }
        Money aggregate = Money.ZERO;
        for (EmployerFundContribution contribution : contributions) {
            PerquisiteNode.requireEnteredEmployment(ctx, contribution.employment(), "employer contribution");
            aggregate = aggregate.plus(contribution.amount());
        }
        TemporalContext t = ctx.inputs().temporal();
        RuleRow rule = ctx.rule(RuleIds.PERQ_EMPLOYER_CONTRIBUTION);
        CapResult excess = TemporallyApportionedCap.apply(rule, t, CapContext.of(CONTRIBUTIONS, aggregate));
        return NodeResult.of(MoneyValue.of(excess.value()), TraceLine.of(1, produces(),
                        RuleIds.PERQ_EMPLOYER_CONTRIBUTION, StepKind.INCOME, MoneyValue.of(aggregate),
                        MoneyValue.of(excess.value()), TemporallyApportionedCap.governingVersion(rule, t).provenance())
                .withLimitedBy(excess.limitedBy()));
    }
}
