package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.EmployerChildEducation;
import com.thiru.wealthlens.taxengine.input.EmployerHouseholdBenefit;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.condition.ItemFacts;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.ItemFactId;
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
import java.util.stream.Collectors;

/**
 * Household services, gas, electricity and water, education, and a carrier's journeys, for {@link PerquisiteNode}
 * (S4 r.15(4) Table III; Rules 1962 r.3(3)–(6); D-120).
 *
 * <p>Each is its amount less the recovery, on the row of {@code RS-PERQ-HOUSEHOLD} its facts select; one no row
 * values is refused (W-154b). Whether a benefit needs a specified employee is {@code PERQ-SPECIFIED-EMPLOYEE}'s
 * condition on its arrangement (OQ-10). A child's education in the employer's institution, or free by reason of the
 * employment, is provided by the employer; it is nil while its cost per month is within the limit, and salary in full
 * above it (D-120 C3).
 */
final class HouseholdBenefitPerquisite {

    static final Set<RuleSetId> RULES = Set.of(RuleSetIds.RS_PERQ_HOUSEHOLD, RuleSetIds.RS_PERQ_EDUCATION_FREE);

    private static final BaseId BENEFIT_VALUE = new BaseId("BENEFIT_VALUE");

    /** What a Table III row is chosen by: the benefit's kind, and for a carrier's journey who the carrier is (D-325). */
    private static final ItemFactId KIND = new ItemFactId("household.kind");
    private static final ItemFactId CARRIER_IS_AIRLINE_OR_RAILWAYS = new ItemFactId(
            "household.carrier.airline.or.railways");

    /** A child's education as the specified-employee row reads it: provided by the employer, by the input's definition. */
    private static final ItemFacts CHILD_EDUCATION = ItemFacts.of(SpecifiedEmployee.ARRANGEMENT, "EMPLOYER_PROVIDED");

    private HouseholdBenefitPerquisite() {
    }

    /** One line per benefit, then one per child's education, numbered from {@code firstSequence}. */
    static List<TraceLine> lines(NodeContext ctx, int firstSequence) {
        List<TraceLine> lines = new ArrayList<>();
        List<EmployerHouseholdBenefit> benefits = ctx.inputs().entered().householdBenefits();
        for (int i = 0; i < benefits.size(); i++) {
            EmployerHouseholdBenefit benefit = benefits.get(i);
            PerquisiteNode.requireEnteredEmployment(ctx, benefit.employment(), "household benefit");
            ItemFacts facts = factsOf(benefit);
            int position = i + 1;
            RuleRow row = ctx.optionalRuleOf(RuleSetIds.RS_PERQ_HOUSEHOLD, facts)
                    .orElseThrow(() -> unvalued(ctx, position, facts));
            int sequence = firstSequence + lines.size();
            lines.add(SpecifiedEmployee.gates(ctx, facts)
                            && !SpecifiedEmployee.holdsFor(ctx, benefit.employment())
                    ? SpecifiedEmployee.notSpecifiedLine(ctx, sequence)
                    : valued(ctx, row, benefit, sequence));
        }
        for (EmployerChildEducation child : ctx.inputs().entered().childEducation()) {
            PerquisiteNode.requireEnteredEmployment(ctx, child.employment(), "child's education");
            int sequence = firstSequence + lines.size();
            lines.add(SpecifiedEmployee.gates(ctx, CHILD_EDUCATION)
                            && !SpecifiedEmployee.holdsFor(ctx, child.employment())
                    ? SpecifiedEmployee.notSpecifiedLine(ctx, sequence)
                    : educated(ctx, child, sequence));
        }
        return lines;
    }

    /** The benefit's facts as the rows' conditions read them. */
    private static ItemFacts factsOf(EmployerHouseholdBenefit benefit) {
        return new ItemFacts(Map.of(KIND, benefit.kind().name(), CARRIER_IS_AIRLINE_OR_RAILWAYS,
                Boolean.toString(benefit.carrierIsAirlineOrRailways()), SpecifiedEmployee.ARRANGEMENT,
                benefit.arrangement().name()), Map.of(), Map.of());
    }

    /** A benefit no row of the year values — the reason is the rows' (the carrier's row's note, for one). */
    private static IllegalArgumentException unvalued(NodeContext ctx, int position, ItemFacts facts) {
        String described = facts.codes().entrySet().stream()
                .map(fact -> fact.getKey() + "=" + fact.getValue())
                .sorted()
                .collect(Collectors.joining(", ", "{", "}"));
        return new IllegalArgumentException("Household benefit " + position + ": no row of "
                + RuleSetIds.RS_PERQ_HOUSEHOLD + " values it in TY " + ctx.inputs().taxYear().label()
                + " — its facts " + described);
    }

    private static TraceLine valued(NodeContext ctx, RuleRow row, EmployerHouseholdBenefit benefit, int sequence) {
        Money net = benefit.amount().minus(benefit.recovered()).floorAtZero();
        Money value = TemporallyApportionedCap.apply(row, ctx.inputs().temporal(),
                CapContext.of(BENEFIT_VALUE, net)).value();
        return ctx.traceLine(sequence, row.id(), StepKind.INCOME, benefit.amount(), value)
                .withLimitedBy(benefit.kind() + " (" + benefit.arrangement() + "), less the recovery "
                        + benefit.recovered());
    }

    private static TraceLine educated(NodeContext ctx, EmployerChildEducation child, int sequence) {
        Money limit = TemporallyApportionedCap.apply(ctx.ruleOf(RuleSetIds.RS_PERQ_EDUCATION_FREE), ctx.inputs().temporal(),
                new CapContext(Map.of())).value();
        Money cost = child.similarInstitutionCostPerMonth();
        Money whole = cost.times(child.months());
        if (!cost.isGreaterThan(limit)) {
            return ctx.traceLine(sequence, ctx.ruleIdOf(RuleSetIds.RS_PERQ_EDUCATION_FREE), StepKind.INCOME,
                            whole, Money.ZERO)
                    .withLimitedBy("not a perquisite: " + cost + " a month does not exceed " + limit + " per child");
        }
        return ctx.traceLine(sequence, ctx.ruleIdOf(RuleSetIds.RS_PERQ_EDUCATION_FREE), StepKind.INCOME,
                        whole, whole.minus(child.recovered()).floorAtZero())
                .withLimitedBy(cost + " a month exceeds " + limit + " per child, so the whole for " + child.months()
                        + " months, less the recovery " + child.recovered());
    }

}
