package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.EmployerMeals;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapResult;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
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

    static final Set<RuleSetId> RULES = Set.of(RuleSetIds.RS_PERQ_MEAL, RuleSetIds.RS_PERQ_MEAL_AT_WORK, RuleSetIds.RS_PERQ_MEAL_VOUCHER);

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
                case AT_WORK -> beyondTheLimit(ctx, ctx.ruleIdOf(RuleSetIds.RS_PERQ_MEAL_AT_WORK), meals, sequence);
                case PAID_VOUCHER -> appliesInRegime(ctx, ctx.ruleIdOf(RuleSetIds.RS_PERQ_MEAL_VOUCHER))
                        ? beyondTheLimit(ctx, ctx.ruleIdOf(RuleSetIds.RS_PERQ_MEAL_VOUCHER), meals, sequence)
                        : inFull(ctx, meals, sequence, "paid vouchers: " + ctx.ruleIdOf(RuleSetIds.RS_PERQ_MEAL_VOUCHER)
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
        return ctx.traceLine(sequence, ruleId, StepKind.INCOME, total(meals), valued.value())
                .withLimitedBy(counted(valued.limitedBy(), meals));
    }

    private static TraceLine inFull(NodeContext ctx, EmployerMeals meals, int sequence, String why) {
        CapResult valued = TemporallyApportionedCap.apply(ctx.ruleOf(RuleSetIds.RS_PERQ_MEAL), ctx.inputs().temporal(),
                CapContext.of(MEAL_VALUE, total(meals)));
        return ctx.traceLine(sequence, ctx.ruleIdOf(RuleSetIds.RS_PERQ_MEAL), StepKind.INCOME,
                        total(meals), valued.value())
                .withLimitedBy(counted(why, meals));
    }

    private static TraceLine notSalary(NodeContext ctx, EmployerMeals meals, int sequence, String why) {
        return ctx.traceLine(sequence, ctx.ruleIdOf(RuleSetIds.RS_PERQ_MEAL), StepKind.INCOME, total(meals), Money.ZERO)
                .withLimitedBy(counted("not a perquisite: " + why, meals));
    }

    private static boolean appliesInRegime(NodeContext ctx, RuleId ruleId) {
        TemporalContext t = ctx.inputs().temporal();
        return ctx.inRegime(ruleId, TemporallyApportionedCap.governingVersion(ctx.rule(ruleId), t)).applies();
    }

    private static Money total(EmployerMeals meals) {
        return meals.valuePerMeal().times(meals.meals());
    }


    /** The limit text with the meals it valued, as every meal line states them. */
    private static String counted(String bound, EmployerMeals meals) {
        return bound + " (" + meals.meals() + " meals at " + meals.valuePerMeal() + ")";
    }
}
