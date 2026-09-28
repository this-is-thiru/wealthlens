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
 * Income under "Salaries": the income before medical travel abroad, plus the part of an employer's spending on
 * medical treatment abroad that is a perquisite (S2 s.19(1); s.17(2)(f), (3); 1961 s.17(2) proviso (vi); D-112).
 *
 * <p>Untraced, as {@code NetPayableNode} is: both figures already carry their lines, and no rule states their sum.
 */
public final class SalaryIncomeNode implements ComputationNode {

    @Override
    public NodeId produces() {
        return NodeIds.SALARY_INCOME;
    }

    @Override
    public Set<NodeId> requires() {
        return Set.of(NodeIds.SALARY_INCOMEBEFORETRAVEL, NodeIds.SALARY_MEDICALTRAVEL);
    }

    @Override
    public Set<RuleId> rules() {
        return Set.of();
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        Money beforeTravel = ctx.get(NodeIds.SALARY_INCOMEBEFORETRAVEL, MoneyValue.class).asMoney();
        Money medicalTravel = ctx.get(NodeIds.SALARY_MEDICALTRAVEL, MoneyValue.class).asMoney();
        return NodeResult.untraced(MoneyValue.of(beforeTravel.plus(medicalTravel)));
    }
}
