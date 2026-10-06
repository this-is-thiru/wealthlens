package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.EmployerFundContribution;
import com.thiru.wealthlens.taxengine.input.RetirementFundHistory;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The employer's retirement contributions that are salary: their aggregate, across every employment, beyond the
 * limit in a tax year (S2 s.17(1)(h); 1961 s.17(2)(vii); {@code DR 01} PF-AGGCAP-001; D-126).
 *
 * <p>The contributions to a recognised provident fund, the notified pension scheme and an approved superannuation fund
 * are added up; only the excess is a perquisite, for every employee and in every regime, and gross salary adds it on
 * a line of its own. The interest, dividend or similar accretion on the excess — this year's and earlier years' — is
 * salary too, by the Rule's formula (S2 s.17(1)(i), S4 r.16; 1961 s.17(2)(viia), Rules 1962 r.3B;
 * {@code DR 01} PF-AGGCAP-002; D-127), on a second line. The recognised fund's own charges are a further item
 * (W-073c); the employer-NPS deduction is Chapter VIII's and is not netted here.
 */
public final class EmployerContributionNode implements ComputationNode {

    private static final BaseId CONTRIBUTIONS = new BaseId("CONTRIBUTIONS");
    private static final BaseId ACCRETION = new BaseId("ACCRETION");

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
        return Set.of(RuleIds.PERQ_EMPLOYER_CONTRIBUTION, RuleIds.PERQ_EMPLOYER_ACCRETION);
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        List<EmployerFundContribution> contributions = ctx.inputs().entered().employerContributions();
        Optional<RetirementFundHistory> history = ctx.inputs().entered().retirementFundHistory();
        if (contributions.isEmpty() && history.isEmpty()) {
            return NodeResult.untraced(MoneyValue.of(Money.ZERO));
        }
        TemporalContext t = ctx.inputs().temporal();
        List<TraceLine> lines = new ArrayList<>();
        Money excess = Money.ZERO;
        if (!contributions.isEmpty()) {
            Money aggregate = Money.ZERO;
            for (EmployerFundContribution contribution : contributions) {
                PerquisiteNode.requireEnteredEmployment(ctx, contribution.employment(), "employer contribution");
                aggregate = aggregate.plus(contribution.amount());
            }
            RuleRow rule = ctx.rule(RuleIds.PERQ_EMPLOYER_CONTRIBUTION);
            CapResult capped = TemporallyApportionedCap.apply(rule, t, CapContext.of(CONTRIBUTIONS, aggregate));
            excess = capped.value();
            lines.add(TraceLine.of(1, produces(), RuleIds.PERQ_EMPLOYER_CONTRIBUTION, StepKind.INCOME,
                            MoneyValue.of(aggregate), MoneyValue.of(excess),
                            TemporallyApportionedCap.governingVersion(rule, t).provenance())
                    .withLimitedBy(capped.limitedBy()));
        }
        if (!excess.isGreaterThan(Money.ZERO) && history.isEmpty()) {
            return new NodeResult(MoneyValue.of(excess), lines);
        }
        RetirementFundHistory fund = history.orElseThrow(() -> new IllegalArgumentException(
                "The accretion on employer contributions beyond the limit (S2 s.17(1)(i); 1961 s.17(2)(viia)) needs"
                        + " the funds' income and balances for the year; enter the retirement fund history"));
        Money earlier = Money.min(fund.earlierExcessContributions().plus(fund.earlierTaxedAccretions()),
                fund.openingBalance());
        RuleRow rule = ctx.rule(RuleIds.PERQ_EMPLOYER_ACCRETION);
        Money accretion = TemporallyApportionedCap.apply(rule, t,
                CapContext.of(ACCRETION, accretion(excess, earlier, fund))).value();
        lines.add(TraceLine.of(lines.size() + 1, produces(), RuleIds.PERQ_EMPLOYER_ACCRETION, StepKind.INCOME,
                        MoneyValue.of(fund.incomeAccrued()), MoneyValue.of(accretion),
                        TemporallyApportionedCap.governingVersion(rule, t).provenance())
                .withLimitedBy("(PC " + excess + " ÷ 2 + PC1 + TP1 " + earlier + ") × I " + fund.incomeAccrued()
                        + " ÷ F(avg) of " + fund.openingBalance() + " and " + fund.closingBalance()));
        return new NodeResult(MoneyValue.of(excess.plus(accretion)), lines);
    }

    /**
     * TP = (PC/2) × R + (PC1 + TP1) × R, R = I ÷ F(avg), F(avg) the average of the opening and closing balances (S4
     * r.16(1); Rules 1962 r.3B), with PC1 + TP1 already capped at the opening balance (r.16(2)(b); r.3B's Note). Both
     * halvings are cleared by doubling the other side, so the one division is (PC + 2 × (PC1 + TP1)) × I ÷ (opening
     * + closing), rounded once.
     */
    private static Money accretion(Money excess, Money earlier, RetirementFundHistory fund) {
        Money relating = excess.plus(earlier.times(2));
        if (!relating.isGreaterThan(Money.ZERO)) {
            return Money.ZERO;
        }
        Money balances = fund.openingBalance().plus(fund.closingBalance());
        if (!balances.isGreaterThan(Money.ZERO)) {
            throw new IllegalArgumentException("The accretion needs the funds' balances: the opening and closing"
                    + " balances are both nil, so their average F(avg) cannot divide the year's income");
        }
        return relating.scaledBy(fund.incomeAccrued(), balances);
    }
}
