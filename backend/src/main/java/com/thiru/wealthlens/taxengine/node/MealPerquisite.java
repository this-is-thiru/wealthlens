package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.EmployerMeals;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapResult;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Free food and non-alcoholic beverages as a perquisite, for {@link PerquisiteNode} (S4 r.15(5) Table IV Sl. 3; Rules
 * 1962 r.3(7)(iii); D-117).
 *
 * <p>The value is the cost less what the employee paid. Food at work, or through paid vouchers, is salary only beyond
 * the limit per meal; tea or snacks at work and food at work in a remote area or off-shore installation are not
 * salary; other food is salary in full. Whether the voucher limit applies in the regime is its row's (TY 2025-26
 * bars the new regime); where it does not, a voucher is valued in full.
 */
final class MealPerquisite {

    static final Set<RuleId> RULES = Set.of(RuleIds.PERQ_MEAL, RuleIds.PERQ_MEAL_AT_WORK, RuleIds.PERQ_MEAL_VOUCHER);

    private static final BaseId MEAL_VALUE = new BaseId("MEAL_VALUE");
    private static final BaseId MEAL = new BaseId("MEAL");

    private MealPerquisite() {
    }

    /** One line per entry, numbered from {@code firstSequence}. */
    static List<TraceLine> lines(NodeContext ctx, int firstSequence) {
        List<TraceLine> lines = new ArrayList<>();
        for (EmployerMeals meals : ctx.inputs().entered().meals()) {
            PerquisiteNode.requireEnteredEmployment(ctx, meals.employment(), "meals");
            int sequence = firstSequence + lines.size();
            lines.add(switch (meals.setting()) {
                case AT_WORK -> beyondTheLimit(ctx, RuleIds.PERQ_MEAL_AT_WORK, meals, sequence);
                case PAID_VOUCHER -> appliesInRegime(ctx, RuleIds.PERQ_MEAL_VOUCHER)
                        ? beyondTheLimit(ctx, RuleIds.PERQ_MEAL_VOUCHER, meals, sequence)
                        : inFull(ctx, meals, sequence, "paid vouchers: " + RuleIds.PERQ_MEAL_VOUCHER
                                + "'s limit does not apply in " + ctx.regime().code());
                case TEA_OR_SNACKS_AT_WORK -> notSalary(ctx, meals, sequence, "tea or snacks during working hours");
                case AT_WORK_REMOTE_OR_OFFSHORE -> notSalary(ctx, meals, sequence,
                        "food during working hours in a remote area or an off-shore installation");
                case OTHERWISE -> inFull(ctx, meals, sequence, "food other than at work or by voucher");
            });
        }
        return lines;
    }

    private static TraceLine beyondTheLimit(NodeContext ctx, RuleId ruleId, EmployerMeals meals, int sequence) {
        CapResult valued = TemporallyApportionedCap.apply(ctx.rule(ruleId), ctx.inputs().temporal(),
                new CapContext(Map.of(MEAL_VALUE, total(meals)), Map.of(), Map.of(MEAL, meals.meals())));
        return line(ctx, ruleId, meals, valued.value(), sequence, valued.limitedBy());
    }

    private static TraceLine inFull(NodeContext ctx, EmployerMeals meals, int sequence, String why) {
        CapResult valued = TemporallyApportionedCap.apply(ctx.rule(RuleIds.PERQ_MEAL), ctx.inputs().temporal(),
                CapContext.of(MEAL_VALUE, total(meals)));
        return line(ctx, RuleIds.PERQ_MEAL, meals, valued.value(), sequence, why);
    }

    private static TraceLine notSalary(NodeContext ctx, EmployerMeals meals, int sequence, String why) {
        return line(ctx, RuleIds.PERQ_MEAL, meals, Money.ZERO, sequence, "not a perquisite: " + why);
    }

    private static boolean appliesInRegime(NodeContext ctx, RuleId ruleId) {
        TemporalContext t = ctx.inputs().temporal();
        return ctx.inRegime(ruleId, TemporallyApportionedCap.governingVersion(ctx.rule(ruleId), t)).applies();
    }

    private static Money total(EmployerMeals meals) {
        return meals.valuePerMeal().times(meals.meals());
    }

    private static TraceLine line(NodeContext ctx, RuleId ruleId, EmployerMeals meals, Money value, int sequence,
            String bound) {
        RuleVersion version = TemporallyApportionedCap.governingVersion(ctx.rule(ruleId), ctx.inputs().temporal());
        return TraceLine.of(sequence, NodeIds.SALARY_PERQUISITES, ruleId, StepKind.INCOME,
                        MoneyValue.of(total(meals)), MoneyValue.of(value), version.provenance())
                .withLimitedBy(bound + " (" + meals.meals() + " meals at " + meals.valuePerMeal() + ")");
    }
}
