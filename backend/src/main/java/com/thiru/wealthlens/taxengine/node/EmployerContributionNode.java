package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.EmployerFund;
import com.thiru.wealthlens.taxengine.input.EmployerFundContribution;
import com.thiru.wealthlens.taxengine.input.RecognisedFundInterest;
import com.thiru.wealthlens.taxengine.input.RetirementFundHistory;
import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.SalaryBases;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapResult;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * The employer's retirement contributions that are salary: their aggregate, across every employment, beyond the
 * limit in a tax year (S2 s.17(1)(h); 1961 s.17(2)(vii); {@code DR 01} PF-AGGCAP-001; D-126).
 *
 * <p>The contributions to a recognised provident fund, the notified pension scheme and an approved superannuation fund
 * are added up; only the excess is a perquisite, for every employee and in every regime, and gross salary adds it on
 * a line of its own. The interest, dividend or similar accretion on the excess — this year's and earlier years' — is
 * salary too, by the Rule's formula (S2 s.17(1)(i), S4 r.16; 1961 s.17(2)(viia), Rules 1962 r.3B;
 * {@code DR 01} PF-AGGCAP-002; D-127), on a second line.
 *
 * <p>A recognised provident fund has charges of its own, separate from and additional to the aggregate ({@code DR 01}
 * PF-RPF12-001; D-151): the employer's contribution beyond twelve per cent of the salary that employment pays
 * (1961 Sch. IV Pt A r.6(a) — TY 2025-26 only, the Finance Act, 2026 having removed it from S2 Sch. XI Pt A para 6),
 * and interest the fund credits above the notified rate (r.6(b); para 6 as substituted), each per employment on a
 * line of its own. The employer-NPS deduction is Chapter VIII's and is not netted here.
 */
public final class EmployerContributionNode implements ComputationNode {

    private static final BaseId CONTRIBUTIONS = new BaseId("CONTRIBUTIONS");
    private static final BaseId ACCRETION = new BaseId("ACCRETION");
    private static final BaseId CONTRIBUTION = new BaseId("CONTRIBUTION");
    private static final BaseId SALARY = new BaseId("SALARY");
    private static final BaseId INTEREST = new BaseId("INTEREST");
    private static final BaseId BALANCE = new BaseId("BALANCE");

    @Override
    public NodeId produces() {
        return NodeIds.EMPLOYER_CONTRIB_CHARGES;
    }

    @Override
    public Set<NodeId> requires() {
        return Set.of(NodeIds.SALARY_BASE);
    }

    @Override
    public Set<RuleSetId> ruleSets() {
        return Set.of(RuleSetIds.RS_PERQ_EMPLOYER_CONTRIBUTION, RuleSetIds.RS_PERQ_EMPLOYER_ACCRETION,
                RuleSetIds.RS_PERQ_RPF_CONTRIBUTION, RuleSetIds.RS_PERQ_RPF_INTEREST);
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        List<TraceLine> lines = new ArrayList<>();
        Money total = aggregateAndAccretion(ctx, lines)
                .plus(recognisedFundContributions(ctx, lines))
                .plus(recognisedFundInterest(ctx, lines));
        if (lines.isEmpty()) {
            return NodeResult.untraced(MoneyValue.of(total));
        }
        return new NodeResult(MoneyValue.of(total), lines);
    }

    /** The aggregate beyond the limit and the accretion on it, each traced on its line (D-126, D-127). */
    private Money aggregateAndAccretion(NodeContext ctx, List<TraceLine> lines) {
        List<EmployerFundContribution> contributions = ctx.inputs().entered().employerContributions();
        Optional<RetirementFundHistory> history = ctx.inputs().entered().retirementFundHistory();
        if (contributions.isEmpty() && history.isEmpty()) {
            return Money.ZERO;
        }
        TemporalContext t = ctx.inputs().temporal();
        Money excess = Money.ZERO;
        if (!contributions.isEmpty()) {
            Money aggregate = Money.ZERO;
            for (EmployerFundContribution contribution : contributions) {
                PerquisiteNode.requireEnteredEmployment(ctx, contribution.employment(), "employer contribution");
                aggregate = aggregate.plus(contribution.amount());
            }
            RuleRow rule = ctx.ruleOf(RuleSetIds.RS_PERQ_EMPLOYER_CONTRIBUTION);
            CapResult capped = TemporallyApportionedCap.apply(rule, t, CapContext.of(CONTRIBUTIONS, aggregate));
            excess = capped.value();
            lines.add(TraceLine.of(lines.size() + 1, produces(), ctx.ruleIdOf(RuleSetIds.RS_PERQ_EMPLOYER_CONTRIBUTION), StepKind.INCOME,
                            MoneyValue.of(aggregate), MoneyValue.of(excess),
                            TemporallyApportionedCap.governingVersion(rule, t).provenance())
                    .withLimitedBy(capped.limitedBy()));
        }
        if (!excess.isGreaterThan(Money.ZERO) && history.isEmpty()) {
            return excess;
        }
        RetirementFundHistory fund = history.orElseThrow(() -> new IllegalArgumentException(
                "The accretion on employer contributions beyond the limit (S2 s.17(1)(i); 1961 s.17(2)(viia)) needs"
                        + " the funds' income and balances for the year; enter the retirement fund history"));
        Money earlier = Money.min(fund.earlierExcessContributions().plus(fund.earlierTaxedAccretions()),
                fund.openingBalance());
        RuleRow rule = ctx.ruleOf(RuleSetIds.RS_PERQ_EMPLOYER_ACCRETION);
        Money accretion = TemporallyApportionedCap.apply(rule, t,
                CapContext.of(ACCRETION, accretion(excess, earlier, fund))).value();
        lines.add(TraceLine.of(lines.size() + 1, produces(), ctx.ruleIdOf(RuleSetIds.RS_PERQ_EMPLOYER_ACCRETION), StepKind.INCOME,
                        MoneyValue.of(fund.incomeAccrued()), MoneyValue.of(accretion),
                        TemporallyApportionedCap.governingVersion(rule, t).provenance())
                .withLimitedBy("(PC " + excess + " ÷ 2 + PC1 + TP1 " + earlier + ") × I " + fund.incomeAccrued()
                        + " ÷ F(avg) of " + fund.openingBalance() + " and " + fund.closingBalance()));
        return excess.plus(accretion);
    }

    /**
     * Each employment's contribution to its recognised provident fund beyond the share of that employment's salary
     * the year's row allows (1961 Sch. IV Pt A r.6(a)). A year whose pack has no such row has no such charge: the 2025
     * Act lost it to the Finance Act, 2026 s.127(a)(iii) before it commenced.
     */
    private Money recognisedFundContributions(NodeContext ctx, List<TraceLine> lines) {
        Optional<RuleRow> row = ctx.optionalRuleOf(RuleSetIds.RS_PERQ_RPF_CONTRIBUTION);
        if (row.isEmpty()) {
            return Money.ZERO;
        }
        Map<Integer, Money> byEmployment = new TreeMap<>();
        for (EmployerFundContribution contribution : ctx.inputs().entered().employerContributions()) {
            if (contribution.fund() == EmployerFund.RECOGNISED_PROVIDENT_FUND) {
                byEmployment.merge(contribution.employment(), contribution.amount(), Money::plus);
            }
        }
        if (byEmployment.isEmpty()) {
            return Money.ZERO;
        }
        SalaryBases bases = ctx.get(NodeIds.SALARY_BASE, SalaryBases.class);
        TemporalContext t = ctx.inputs().temporal();
        RuleRow rule = row.get();
        Money total = Money.ZERO;
        for (Map.Entry<Integer, Money> entry : byEmployment.entrySet()) {
            Money salary = bases.of(ctx.pack().ruleSet(RuleSetIds.RS_SAL_SALARY_FOR_RPF), entry.getKey());
            CapResult charged = TemporallyApportionedCap.apply(rule, t,
                    new CapContext(Map.of(CONTRIBUTION, entry.getValue(), SALARY, salary)));
            lines.add(TraceLine.of(lines.size() + 1, produces(), rule.id(), StepKind.INCOME,
                            MoneyValue.of(entry.getValue()), MoneyValue.of(charged.value()),
                            TemporallyApportionedCap.governingVersion(rule, t).provenance())
                    .withLimitedBy(charged.limitedBy() + "; employment " + entry.getKey() + "'s salary " + salary));
            total = total.plus(charged.value());
        }
        return total;
    }

    /**
     * Each fund's interest credited in so far as it was allowed above the notified rate (1961 Sch. IV Pt A r.6(b); S2
     * Sch. XI Pt A para 6 as substituted). Interest is a rate on a balance, so the balance it was allowed on is the
     * interest over the rate allowed, and the notified rate on that balance is what the employee may keep.
     */
    private Money recognisedFundInterest(NodeContext ctx, List<TraceLine> lines) {
        TemporalContext t = ctx.inputs().temporal();
        Money total = Money.ZERO;
        for (RecognisedFundInterest interest : ctx.inputs().entered().recognisedFundInterest()) {
            PerquisiteNode.requireEnteredEmployment(ctx, interest.employment(), "recognised fund interest");
            if (!interest.interestCredited().isGreaterThan(Money.ZERO)) {
                continue;
            }
            if (interest.rateAllowed().compareTo(Rate.ZERO) == 0) {
                throw new IllegalArgumentException("Interest of " + interest.interestCredited() + " credited at a"
                        + " rate of nil cannot be: enter the rate the fund allowed it at");
            }
            Money balance = interest.rateAllowed().baseYielding(interest.interestCredited());
            RuleRow rule = ctx.ruleOf(RuleSetIds.RS_PERQ_RPF_INTEREST);
            CapResult charged = TemporallyApportionedCap.apply(rule, t,
                    new CapContext(Map.of(INTEREST, interest.interestCredited(), BALANCE, balance)));
            lines.add(TraceLine.of(lines.size() + 1, produces(), ctx.ruleIdOf(RuleSetIds.RS_PERQ_RPF_INTEREST), StepKind.INCOME,
                            MoneyValue.of(interest.interestCredited()), MoneyValue.of(charged.value()),
                            TemporallyApportionedCap.governingVersion(rule, t).provenance())
                    .withLimitedBy(charged.limitedBy() + "; allowed at " + interest.rateAllowed().asPercentLabel()
                            + " on a balance of " + balance));
            total = total.plus(charged.value());
        }
        return total;
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
