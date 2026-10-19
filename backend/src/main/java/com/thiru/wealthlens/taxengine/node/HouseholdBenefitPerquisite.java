package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.BenefitArrangement;
import com.thiru.wealthlens.taxengine.input.EmployerChildEducation;
import com.thiru.wealthlens.taxengine.input.EmployerHouseholdBenefit;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Household services, gas, electricity and water, education, and a carrier's journeys, for {@link PerquisiteNode}
 * (S4 r.15(4) Table III; Rules 1962 r.3(3)–(6); D-120).
 *
 * <p>Each is its amount less the recovery. What the employer provides itself is a benefit or amenity, salary only for
 * a specified employee; what meets the employee's own obligation is salary for everyone (OQ-10). A child's education
 * in the employer's institution, or free by reason of the employment, is nil while its cost per month is within the
 * limit, and salary in full above it (D-120 C3). An airline's or the railways' employee's journey is refused: the
 * Rule "shall not apply" and says nothing of what does.
 */
final class HouseholdBenefitPerquisite {

    static final Set<RuleSetId> RULES = Set.of(RuleSetIds.RS_PERQ_SERVICES, RuleSetIds.RS_PERQ_UTILITY, RuleSetIds.RS_PERQ_EDUCATION,
            RuleSetIds.RS_PERQ_EDUCATION_FREE, RuleSetIds.RS_PERQ_CARRIER_TRAVEL);

    private static final BaseId BENEFIT_VALUE = new BaseId("BENEFIT_VALUE");

    private HouseholdBenefitPerquisite() {
    }

    /** One line per benefit, then one per child's education, numbered from {@code firstSequence}. */
    static List<TraceLine> lines(NodeContext ctx, int firstSequence) {
        List<TraceLine> lines = new ArrayList<>();
        for (EmployerHouseholdBenefit benefit : ctx.inputs().entered().householdBenefits()) {
            PerquisiteNode.requireEnteredEmployment(ctx, benefit.employment(), "household benefit");
            int sequence = firstSequence + lines.size();
            if (benefit.arrangement() == BenefitArrangement.EMPLOYER_PROVIDED
                    && !SpecifiedEmployee.holdsFor(ctx, benefit.employment())) {
                lines.add(SpecifiedEmployee.notSpecifiedLine(ctx, sequence));
                continue;
            }
            lines.add(valued(ctx, benefit, sequence));
        }
        for (EmployerChildEducation child : ctx.inputs().entered().childEducation()) {
            PerquisiteNode.requireEnteredEmployment(ctx, child.employment(), "child's education");
            int sequence = firstSequence + lines.size();
            lines.add(SpecifiedEmployee.holdsFor(ctx, child.employment())
                    ? educated(ctx, child, sequence)
                    : SpecifiedEmployee.notSpecifiedLine(ctx, sequence));
        }
        return lines;
    }

    private static TraceLine valued(NodeContext ctx, EmployerHouseholdBenefit benefit, int sequence) {
        RuleId ruleId = switch (benefit.kind()) {
            case SERVICES -> ctx.ruleIdOf(RuleSetIds.RS_PERQ_SERVICES);
            case UTILITY_FROM_OUTSIDE_AGENCY, UTILITY_FROM_EMPLOYER_RESOURCES -> ctx.ruleIdOf(RuleSetIds.RS_PERQ_UTILITY);
            case EDUCATION_FEES -> ctx.ruleIdOf(RuleSetIds.RS_PERQ_EDUCATION);
            case CARRIER_TRAVEL -> ctx.ruleIdOf(RuleSetIds.RS_PERQ_CARRIER_TRAVEL);
        };
        if (benefit.carrierIsAirlineOrRailways()) {
            throw new IllegalArgumentException("Free travel for an employee of an airline or the railways is outside"
                    + " Table III Sl. 4 / Rules 1962 r.3(6), and neither says how it is valued; it cannot be valued"
                    + " (D-120 C4a)");
        }
        Money net = benefit.amount().minus(benefit.recovered()).floorAtZero();
        Money value = TemporallyApportionedCap.apply(ctx.rule(ruleId), ctx.inputs().temporal(),
                CapContext.of(BENEFIT_VALUE, net)).value();
        return line(ctx, ruleId, benefit.amount(), value, sequence,
                benefit.kind() + " (" + benefit.arrangement() + "), less the recovery " + benefit.recovered());
    }

    private static TraceLine educated(NodeContext ctx, EmployerChildEducation child, int sequence) {
        Money limit = TemporallyApportionedCap.apply(ctx.ruleOf(RuleSetIds.RS_PERQ_EDUCATION_FREE), ctx.inputs().temporal(),
                new CapContext(Map.of())).value();
        Money cost = child.similarInstitutionCostPerMonth();
        Money whole = cost.times(child.months());
        if (!cost.isGreaterThan(limit)) {
            return line(ctx, ctx.ruleIdOf(RuleSetIds.RS_PERQ_EDUCATION_FREE), whole, Money.ZERO, sequence,
                    "not a perquisite: " + cost + " a month does not exceed " + limit + " per child");
        }
        return line(ctx, ctx.ruleIdOf(RuleSetIds.RS_PERQ_EDUCATION_FREE), whole, whole.minus(child.recovered()).floorAtZero(), sequence,
                cost + " a month exceeds " + limit + " per child, so the whole for " + child.months()
                        + " months, less the recovery " + child.recovered());
    }

    private static TraceLine line(NodeContext ctx, RuleId ruleId, Money in, Money value, int sequence, String bound) {
        return TraceLine.of(sequence, NodeIds.SALARY_PERQUISITES, ruleId, StepKind.INCOME, MoneyValue.of(in),
                        MoneyValue.of(value),
                        TemporallyApportionedCap.governingVersion(ctx.rule(ruleId), ctx.inputs().temporal())
                                .provenance())
                .withLimitedBy(bound);
    }
}
