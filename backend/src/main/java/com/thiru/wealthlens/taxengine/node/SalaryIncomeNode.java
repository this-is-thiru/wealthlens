package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Set;

/**
 * Income under "Salaries": gross salary less the exemptions and the deductions from salary (S2 s.19(1),
 * "computed after making the deductions"; {@code DR 06} G1.1 step 12; D-096, D-099).
 *
 * <p>Untraced, as {@code NetPayableNode} is: both figures already carry their lines, and no rule states their
 * difference. The retirement deductions ({@code W-066a}) join the subtraction with their own item. A result below zero is refused rather than floored: the Act has no loss under this
 * head, so a negative figure means the entries are wrong, and hiding it as nil would pass that on.
 */
public final class SalaryIncomeNode implements ComputationNode {

    @Override
    public NodeId produces() {
        return NodeIds.SALARY_INCOME;
    }

    @Override
    public Set<NodeId> requires() {
        return Set.of(NodeIds.SALARY_GROSS, NodeIds.SALARY_EXEMPTIONS, NodeIds.SALARY_DEDUCTIONS);
    }

    @Override
    public Set<RuleId> rules() {
        return Set.of();
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        Money gross = ctx.get(NodeIds.SALARY_GROSS, MoneyValue.class).asMoney();
        Money exemptions = ctx.get(NodeIds.SALARY_EXEMPTIONS, MoneyValue.class).asMoney();
        Money deductions = ctx.get(NodeIds.SALARY_DEDUCTIONS, MoneyValue.class).asMoney();
        Money taken = exemptions.plus(deductions);
        if (taken.isGreaterThan(gross)) {
            throw new IllegalStateException("The exemptions (" + exemptions + ") and deductions from salary ("
                    + deductions + ") exceed gross salary (" + gross + "); the Act has no loss under \"Salaries\","
                    + " so the entries need correcting");
        }
        return NodeResult.untraced(MoneyValue.of(gross.minus(taken)));
    }
}
