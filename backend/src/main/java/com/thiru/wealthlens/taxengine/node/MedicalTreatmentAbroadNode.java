package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.MedicalTreatmentAbroad;
import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapResult;
import com.thiru.wealthlens.taxengine.rules.condition.Condition;
import com.thiru.wealthlens.taxengine.rules.condition.ConditionContext;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The part of an employer's spending on medical treatment abroad that is a perquisite (S2 s.17(1)(f), (2)(f), (3);
 * 1961 s.17(2)(iv) and proviso (vi); D-112).
 *
 * <p>The spending is excluded from the perquisite on two tests, one row each, and what the exclusion does not reach
 * is salary: treatment and stay only "to the extent permitted by the Reserve Bank of India"; travel only where gross
 * total income "as computed before including therein the said expenditure" does not exceed the prescribed amount —
 * a condition on the travel row. That is why this node runs after {@code salary.incomebeforetravel}.
 *
 * <p><b>The income tested is the salary head's alone</b>, because no other head is built. Gross total income is
 * every head's (S2 s.122(10)); when another head lands, this node must read their total before the travel.
 */
public final class MedicalTreatmentAbroadNode implements ComputationNode {

    private static final BaseId TREATMENT_AND_STAY = new BaseId("TREATMENT_AND_STAY");
    private static final BaseId PERMITTED_BY_RESERVE_BANK = new BaseId("PERMITTED_BY_RESERVE_BANK");
    private static final BaseId TRAVEL = new BaseId("TRAVEL");

    @Override
    public NodeId produces() {
        return NodeIds.SALARY_MEDICALTRAVEL;
    }

    @Override
    public Set<NodeId> requires() {
        return Set.of(NodeIds.SALARY_INCOMEBEFORETRAVEL);
    }

    @Override
    public Set<RuleSetId> ruleSets() {
        return Set.of(RuleSetIds.RS_PERQ_MEDABROAD_STAY, RuleSetIds.RS_PERQ_MEDABROAD_TRAVEL);
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        List<MedicalTreatmentAbroad> claims = ctx.inputs().entered().medicalTreatmentAbroad();
        if (claims.isEmpty()) {
            return NodeResult.untraced(MoneyValue.of(Money.ZERO));
        }
        Money incomeBeforeTravel = ctx.get(NodeIds.SALARY_INCOMEBEFORETRAVEL, MoneyValue.class).asMoney();
        ConditionContext asked = withIncomeBeforeTravel(ctx.conditions(), incomeBeforeTravel);
        List<TraceLine> trace = new ArrayList<>();
        for (MedicalTreatmentAbroad claim : claims) {
            requireEnteredEmployment(ctx, claim);
            trace.add(treatmentAndStay(ctx, claim, trace.size() + 1));
            trace.add(travel(ctx, claim, asked, trace.size() + 1));
        }
        Money value = trace.stream().map(line -> line.out().asMoney()).reduce(Money.ZERO, Money::plus);
        return new NodeResult(MoneyValue.of(value), trace);
    }

    /** Treatment and stay, less what the Reserve Bank of India permitted. */
    private static TraceLine treatmentAndStay(NodeContext ctx, MedicalTreatmentAbroad claim, int sequence) {
        TemporalContext t = ctx.inputs().temporal();
        RuleRow rule = ctx.ruleOf(RuleSetIds.RS_PERQ_MEDABROAD_STAY);
        RuleVersion version = TemporallyApportionedCap.governingVersion(rule, t);
        CapResult excluded = TemporallyApportionedCap.apply(rule, t, new CapContext(Map.of(
                TREATMENT_AND_STAY, claim.treatmentAndStay(),
                PERMITTED_BY_RESERVE_BANK, claim.permittedByReserveBank())));
        Money value = claim.treatmentAndStay().minus(excluded.value());
        return TraceLine.of(sequence, NodeIds.SALARY_MEDICALTRAVEL, ctx.ruleIdOf(RuleSetIds.RS_PERQ_MEDABROAD_STAY), StepKind.INCOME,
                        MoneyValue.of(claim.treatmentAndStay()), MoneyValue.of(value), version.provenance())
                .withLimitedBy("excluded " + excluded.value() + ", " + excluded.limitedBy());
    }

    /** Travel: wholly excluded where the income before it passes the row's condition, else wholly salary. */
    private static TraceLine travel(NodeContext ctx, MedicalTreatmentAbroad claim, ConditionContext asked,
            int sequence) {
        TemporalContext t = ctx.inputs().temporal();
        RuleRow rule = ctx.ruleOf(RuleSetIds.RS_PERQ_MEDABROAD_TRAVEL);
        RuleVersion version = TemporallyApportionedCap.governingVersion(rule, t);
        Optional<Condition> unmet = version.conditions().stream()
                .filter(condition -> !condition.holds(asked))
                .findFirst();
        Money excluded = unmet.isPresent() ? Money.ZERO
                : TemporallyApportionedCap.apply(rule, t, CapContext.of(TRAVEL, claim.travel())).value();
        String bound = unmet.map(condition -> "not excluded, not met: " + condition.describe())
                .orElse("excluded " + excluded + ", " + version.conditions().stream().map(Condition::describe)
                        .toList());
        return TraceLine.of(sequence, NodeIds.SALARY_MEDICALTRAVEL, ctx.ruleIdOf(RuleSetIds.RS_PERQ_MEDABROAD_TRAVEL), StepKind.INCOME,
                        MoneyValue.of(claim.travel()), MoneyValue.of(claim.travel().minus(excluded)),
                        version.provenance())
                .withLimitedBy(bound);
    }

    /** The person facts, with the one computed amount the travel row's condition reads. */
    private static ConditionContext withIncomeBeforeTravel(ConditionContext person, Money incomeBeforeTravel) {
        return new ConditionContext(person.resident(), person.personType(), person.ages(), person.disabilities(),
                Map.of(NodeIds.SALARY_INCOMEBEFORETRAVEL, incomeBeforeTravel), Set.of(), Set.of(), Set.of(),
                Optional.empty(), person.item());
    }

    private static void requireEnteredEmployment(NodeContext ctx, MedicalTreatmentAbroad claim) {
        int entered = ctx.inputs().entered().employments().size();
        if (claim.employment() >= entered) {
            throw new IllegalArgumentException("The medical treatment abroad names employment " + claim.employment()
                    + ", but " + entered + " were entered");
        }
    }
}
