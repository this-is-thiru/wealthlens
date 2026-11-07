package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.EmployerMeals;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapResult;
import com.thiru.wealthlens.taxengine.rules.condition.ItemFacts;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.ItemFactId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
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
 * <p>The value is the cost less what the employee paid. A meal a row of {@code RS-PERQ-MEAL-EXCLUDED} reaches is not
 * salary; one a row of {@code RS-PERQ-MEAL-LIMIT} reaches is salary only beyond the limit per meal; any other is salary
 * in full on {@code PERQ-MEAL} — each row reaching its setting by {@code meals.setting} (W-154c). A limit row the
 * regime does not apply in reaches nothing there, so such a meal is valued in full.
 */
final class MealPerquisite {

    static final Set<RuleSetId> RULES = Set.of(RuleSetIds.RS_PERQ_MEAL, RuleSetIds.RS_PERQ_MEAL_EXCLUDED,
            RuleSetIds.RS_PERQ_MEAL_LIMIT);

    /** What the exclusion and limit rows are chosen by: where and how the food is provided (D-325). */
    private static final ItemFactId SETTING = new ItemFactId("meals.setting");

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
            ItemFacts facts = ItemFacts.of(SETTING, meals.setting().name());
            lines.add(ctx.optionalRuleOf(RuleSetIds.RS_PERQ_MEAL_EXCLUDED, facts)
                    .map(row -> excluded(ctx, row, meals, sequence))
                    .or(() -> ctx.optionalRuleOf(RuleSetIds.RS_PERQ_MEAL_LIMIT, facts)
                            .map(row -> beyondTheLimit(ctx, row.id(), meals, sequence)))
                    .orElseGet(() -> inFull(ctx, meals, sequence)));
        }
        return lines;
    }

    private static TraceLine beyondTheLimit(NodeContext ctx, RuleId ruleId, EmployerMeals meals, int sequence) {
        CapResult valued = TemporallyApportionedCap.apply(ctx.rule(ruleId), ctx.inputs().temporal(),
                new CapContext(Map.of(MEAL_VALUE, total(meals)), Map.of(), Map.of(MEAL, meals.meals())));
        return ctx.traceLine(sequence, ruleId, StepKind.INCOME, total(meals), valued.value())
                .withLimitedBy(counted(valued.limitedBy(), meals));
    }

    /** Food no exclusion or limit row reaches this year and regime: salary in full. */
    private static TraceLine inFull(NodeContext ctx, EmployerMeals meals, int sequence) {
        String why = meals.setting() + ": in full, no row of " + RuleSetIds.RS_PERQ_MEAL_LIMIT + " reaching it in "
                + ctx.regime().code();
        CapResult valued = TemporallyApportionedCap.apply(ctx.ruleOf(RuleSetIds.RS_PERQ_MEAL), ctx.inputs().temporal(),
                CapContext.of(MEAL_VALUE, total(meals)));
        return ctx.traceLine(sequence, ctx.ruleIdOf(RuleSetIds.RS_PERQ_MEAL), StepKind.INCOME,
                        total(meals), valued.value())
                .withLimitedBy(counted(why, meals));
    }

    private static TraceLine excluded(NodeContext ctx, RuleRow row, EmployerMeals meals, int sequence) {
        return ctx.traceLine(sequence, row.id(), StepKind.INCOME, total(meals), Money.ZERO)
                .withLimitedBy(counted("not a perquisite: excluded by " + row.id(), meals));
    }

    private static Money total(EmployerMeals meals) {
        return meals.valuePerMeal().times(meals.meals());
    }


    /** The limit text with the meals it valued, as every meal line states them. */
    private static String counted(String bound, EmployerMeals meals) {
        return bound + " (" + meals.meals() + " meals at " + meals.valuePerMeal() + ")";
    }
}
