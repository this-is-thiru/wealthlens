package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.CompanyEmployer;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.Map;

/**
 * Whether a benefit or amenity is a perquisite for this employee at all — the test every s.17(1)(c) benefit reads
 * (S2 s.17(1)(c); 1961 s.17(2)(iii); D-113, D-120; OQ-10), shared by the car and the Table III benefits.
 *
 * <p>A specified employee is a director or substantial-interest holder of a company employer, or one whose income
 * under "Salaries" by way of monetary payment ({@code salary.monetaryincome}) exceeds the prescribed amount
 * ({@code PERQ-SPECIFIED-EMPLOYEE}), strictly. A node calling it declares that node and that rule.
 */
final class SpecifiedEmployee {

    private SpecifiedEmployee() {
    }

    /** Whether the employee of this employment is a specified employee. */
    static boolean holdsFor(NodeContext ctx, int employment) {
        boolean byStanding = ctx.inputs().entered().employments().get(employment).companyEmployer()
                .map(CompanyEmployer::makesEveryBenefitAPerquisite)
                .orElse(false);
        return byStanding || monetaryIncome(ctx).isGreaterThan(threshold(ctx));
    }

    /** A nil line for a benefit that is not a perquisite because the employee is not a specified one. */
    static TraceLine notSpecifiedLine(NodeContext ctx, int sequence) {
        return TraceLine.of(sequence, NodeIds.SALARY_PERQUISITES, RuleIds.PERQ_SPECIFIED_EMPLOYEE, StepKind.INCOME,
                        MoneyValue.of(monetaryIncome(ctx)), MoneyValue.of(Money.ZERO),
                        TemporallyApportionedCap.governingVersion(ctx.rule(RuleIds.PERQ_SPECIFIED_EMPLOYEE),
                                ctx.inputs().temporal()).provenance())
                .withLimitedBy("not a specified employee: salary by way of monetary payment " + monetaryIncome(ctx)
                        + " does not exceed " + threshold(ctx) + ", and no director or substantial interest");
    }

    private static Money monetaryIncome(NodeContext ctx) {
        return ctx.get(NodeIds.SALARY_MONETARYINCOME, MoneyValue.class).asMoney();
    }

    private static Money threshold(NodeContext ctx) {
        return TemporallyApportionedCap.apply(ctx.rule(RuleIds.PERQ_SPECIFIED_EMPLOYEE), ctx.inputs().temporal(),
                new CapContext(Map.of())).value();
    }
}
