package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.HashSet;
import java.util.Set;

/**
 * Income under "Salaries" by way of monetary payment, from every employment — the measure that makes an employee a
 * specified employee when it exceeds the prescribed amount (S2 s.17(1)(c)(ii); 1961 s.17(2)(iii)(c), "exclusive of
 * the value of all benefits or amenities not provided for by way of monetary payment"; D-113 C3a).
 *
 * <p>It is the head's income with the perquisites left out: the pay the head totals, less the exemptions, the
 * retirement deductions and the deductions from salary applied to that pay — the standard deduction capped at it.
 * It runs before {@code salary.perquisites}, which reads it; that is why it cannot be {@code salary.income}, which
 * the perquisites feed. Untraced: it decides a test, and the perquisite line that reads it names the figure.
 */
public final class MonetarySalaryIncomeNode implements ComputationNode {

    private static final Set<RuleSetId> RULES = rulesRead();

    private static Set<RuleSetId> rulesRead() {
        Set<RuleSetId> rules = new HashSet<>(SalaryDeductionNode.RULES);
        rules.add(RuleSetIds.RS_SAL_SALARY_INCLUDES);
        return Set.copyOf(rules);
    }

    @Override
    public NodeId produces() {
        return NodeIds.SALARY_MONETARYINCOME;
    }

    @Override
    public Set<NodeId> requires() {
        return Set.of(NodeIds.INPUTS_COMPONENTS, NodeIds.SALARY_BASE, NodeIds.SALARY_EXEMPTIONS,
                NodeIds.SALARY_RETIREMENT);
    }

    @Override
    public Set<RuleSetId> ruleSets() {
        return RULES;
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        Money pay = GrossSalaryNode.pay(ctx);
        Money exemptions = ctx.get(NodeIds.SALARY_EXEMPTIONS, MoneyValue.class).asMoney();
        Money retirement = ctx.get(NodeIds.SALARY_RETIREMENT, MoneyValue.class).asMoney();
        Money deductions = SalaryDeductionNode.on(ctx, pay);
        return NodeResult.untraced(MoneyValue.of(pay.minus(exemptions).minus(retirement).minus(deductions)));
    }
}
