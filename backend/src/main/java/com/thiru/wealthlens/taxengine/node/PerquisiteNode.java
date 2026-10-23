package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.EmployerAccommodation;
import com.thiru.wealthlens.taxengine.input.Furniture;
import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.SalaryBases;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapResult;
import com.thiru.wealthlens.taxengine.rules.condition.ItemFacts;
import com.thiru.wealthlens.taxengine.rules.definition.IndexByYear;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinitions;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.ItemFactId;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.rules.temporal.proration.Days;
import com.thiru.wealthlens.taxengine.rules.temporal.proration.ProrationBasis;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The value of perquisites, which is salary in every regime (S2 s.16, s.17(1); 1961 s.17(1)(iv), (2); {@code DR 06}
 * G1.1 step 3; D-111). Built so far: residential accommodation (S4 r.15(2) Table I; Rules 1962 r.3(1)) and, through
 * {@link MotorCarPerquisite}, a vehicle the employer provides or runs (Table II; r.3(2); D-113) — which reads
 * {@code salary.monetaryincome} for the specified-employee test; meals and gifts through {@link MealPerquisite} and
 * {@link GiftPerquisite} (Table IV Sl. 3–4; r.3(7)(iii)–(iv); D-117); loans through {@link LoanPerquisite} (Sl. 1;
 * r.3(7)(i); D-118); holidays, cards, clubs, other benefits and assets through {@link ExpenseBenefitPerquisite} and
 * {@link AssetPerquisite} (Sl. 2, 5–9; r.3(7)(ii), (v)–(ix); D-119); services, utilities, education and carriers'
 * journeys through {@link HouseholdBenefitPerquisite} (Table III; r.3(3)–(6); D-120); specified securities and sweat
 * equity through {@link SpecifiedSecurityPerquisite} (S2 s.17(1)(d), (4); r.15(6)–(7); r.3(8)–(9); D-121).
 *
 * <p>Each accommodation is valued on its Table row — the licence fee, a percentage of salary by the city's
 * population, the lower of the lease rental or hotel charges and a percentage of salary — "as reduced by the rent …
 * actually paid", plus its furniture. The "salary" is the accommodation base less the allowances the exemptions
 * allowed (r.15(8)(k)(iii)), which is why this node runs after {@code salary.exemptions}; it is apportioned to the
 * period occupied by days, as HRA's is. Accommodation continued from an earlier year is capped, where the cap's row
 * reaches its provider (W-154a), at the first year's amount scaled by the Cost Inflation Index (D-115). A perquisite is provided, not paid: its value is non-cash
 * income, never take-home.
 */
public final class PerquisiteNode implements ComputationNode {

    private static final BaseId SALARY = new BaseId("SALARY");
    private static final BaseId EMPLOYER_COST = new BaseId("EMPLOYER_COST");
    private static final BaseId POPULATION = new BaseId("POPULATION");
    private static final BaseId FURNITURE_COST = new BaseId("FURNITURE_COST");
    private static final BaseId SCALED_FIRST_YEAR_VALUE = new BaseId("SCALED_FIRST_YEAR_VALUE");
    private static final ProrationBasis BY_DAYS_OCCUPIED = new Days();

    /** What an accommodation's Table I row is chosen by (D-253). */
    private static final ItemFactId ACCOMMODATION_PROVIDER = new ItemFactId("accommodation.provider");

    private static final Set<RuleSetId> RULES = rulesRead();

    private static Set<RuleSetId> rulesRead() {
        Set<RuleSetId> rules = new HashSet<>(MotorCarPerquisite.RULES);
        rules.addAll(MealPerquisite.RULES);
        rules.add(RuleSetIds.RS_PERQ_GIFT);
        rules.addAll(LoanPerquisite.RULES);
        rules.addAll(ExpenseBenefitPerquisite.RULES);
        rules.addAll(AssetPerquisite.RULES);
        rules.addAll(HouseholdBenefitPerquisite.RULES);
        rules.addAll(SpecifiedSecurityPerquisite.RULES);
        rules.add(RuleSetIds.RS_PERQ_SPECIFIED_EMPLOYEE);
        rules.addAll(Set.of(RuleSetIds.RS_PERQ_ACCOM, RuleSetIds.RS_PERQ_ACCOM_FURNITURE,
                RuleSetIds.RS_PERQ_ACCOM_CONTINUED, RuleSetIds.RS_RATE_COST_INFLATION_INDEX));
        return Set.copyOf(rules);
    }

    @Override
    public NodeId produces() {
        return NodeIds.SALARY_PERQUISITES;
    }

    @Override
    public Set<NodeId> requires() {
        return Set.of(NodeIds.SALARY_BASE, NodeIds.SALARY_EXEMPTIONS, NodeIds.SALARY_MONETARYINCOME);
    }

    @Override
    public Set<RuleSetId> ruleSets() {
        return RULES;
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        List<EmployerAccommodation> accommodations = ctx.inputs().entered().accommodations();
        List<TraceLine> trace = new ArrayList<>();
        for (EmployerAccommodation accommodation : accommodations) {
            trace.add(accommodation(ctx, accommodation, trace.size() + 1));
            accommodation.furniture().ifPresent(furniture ->
                    trace.add(furniture(ctx, accommodation, furniture, trace.size() + 1)));
        }
        trace.addAll(MotorCarPerquisite.lines(ctx, trace.size() + 1));
        trace.addAll(MealPerquisite.lines(ctx, trace.size() + 1));
        trace.addAll(GiftPerquisite.lines(ctx, trace.size() + 1));
        trace.addAll(LoanPerquisite.lines(ctx, trace.size() + 1));
        trace.addAll(ExpenseBenefitPerquisite.lines(ctx, trace.size() + 1));
        trace.addAll(AssetPerquisite.lines(ctx, trace.size() + 1));
        trace.addAll(HouseholdBenefitPerquisite.lines(ctx, trace.size() + 1));
        trace.addAll(SpecifiedSecurityPerquisite.lines(ctx, trace.size() + 1));
        Money value = trace.stream().map(line -> line.out().asMoney()).reduce(Money.ZERO, Money::plus);
        return trace.isEmpty() ? NodeResult.untraced(MoneyValue.of(Money.ZERO))
                : new NodeResult(MoneyValue.of(value), trace);
    }

    /** One accommodation's Table I value, less the rent the employee paid. */
    private static TraceLine accommodation(NodeContext ctx, EmployerAccommodation accommodation, int sequence) {
        TemporalContext t = ctx.inputs().temporal();
        requireWithinYear(accommodation.occupied(), t);
        ItemFacts facts = ItemFacts.of(ACCOMMODATION_PROVIDER, accommodation.provider().name());
        RuleId ruleId = ctx.ruleIdOf(RuleSetIds.RS_PERQ_ACCOM, facts);
        RuleRow rule = ctx.rule(ruleId);
        RuleVersion version = TemporallyApportionedCap.governingVersion(rule, t);
        Money salary = salaryFor(ctx, accommodation, t);
        Map<BaseId, Money> bases = new HashMap<>(Map.of(SALARY, salary));
        accommodation.employerCost().ifPresent(cost -> bases.put(EMPLOYER_COST, cost));
        Map<BaseId, Integer> population = accommodation.cityPopulation()
                .map(people -> Map.of(POPULATION, people))
                .orElse(Map.of());
        CapResult valued = TemporallyApportionedCap.apply(rule, t,
                new CapContext(Map.copyOf(bases), Map.of(), population));
        Money value = valued.value().minus(accommodation.rentPaid()).floorAtZero();
        String bound = valued.limitedBy() + ", less the rent paid " + accommodation.rentPaid();
        Optional<RuleRow> capRow = ctx.optionalRuleOf(RuleSetIds.RS_PERQ_ACCOM_CONTINUED, facts);
        if (capRow.isPresent() && isContinued(accommodation, t.taxYear())) {
            Money cap = continuedCap(ctx, capRow.get(), accommodation, t);
            if (cap.isLessThan(value)) {
                value = cap;
                bound += "; capped at the first year's amount scaled by the Cost Inflation Index, " + cap;
            }
        }
        return TraceLine.of(sequence, NodeIds.SALARY_PERQUISITES, ruleId, StepKind.INCOME, MoneyValue.of(salary),
                        MoneyValue.of(value), version.provenance())
                .withLimitedBy(bound);
    }

    /**
     * Accommodation provided to the employee before this tax year. Which providers the cap reaches is its row's
     * conditions (W-154a).
     */
    private static boolean isContinued(EmployerAccommodation accommodation, TaxYearId year) {
        return accommodation.firstProvidedIn().startYear() < year.startYear();
    }

    /**
     * The first year's amount, scaled by this year's index over the first year's (D-115). A first year the index
     * table does not hold — any before 2023-24, the first tax year the Rules name — is refused: which year's index
     * then divides is OQ-11.
     */
    private static Money continuedCap(NodeContext ctx, RuleRow capRow, EmployerAccommodation accommodation,
            TemporalContext t) {
        Money firstYearValue = accommodation.firstYearValue().orElseThrow(() -> new IllegalArgumentException(
                "Accommodation continued from " + accommodation.firstProvidedIn() + " is capped at the amount"
                        + " calculated for the first tax year (r.15(2)(d); Rules 1962 r.3(1) third proviso); enter"
                        + " that amount"));
        IndexByYear index = costInflationIndex(ctx, t);
        if (!index.indexByYear().containsKey(accommodation.firstProvidedIn())) {
            throw new IllegalArgumentException("Accommodation first provided in " + accommodation.firstProvidedIn()
                    + " is before the first tax year the Rules name; whether its cap divides by that year's index or"
                    + " the first tax year's is not settled (OQ-11), so it cannot be valued yet (D-115)");
        }
        Money scaled = firstYearValue.scaledBy(index.of(t.taxYear()), index.of(accommodation.firstProvidedIn()));
        return TemporallyApportionedCap.apply(capRow, t,
                CapContext.of(SCALED_FIRST_YEAR_VALUE, scaled)).value();
    }

    private static IndexByYear costInflationIndex(NodeContext ctx, TemporalContext t) {
        RuleId ruleId = ctx.ruleIdOf(RuleSetIds.RS_RATE_COST_INFLATION_INDEX);
        return RuleDefinitions.require(ctx.rule(ruleId).versionAt(t).definition(), IndexByYear.class, ruleId);
    }

    /** Furnished: ten per cent a year of the cost for the period, or the hire charges; less the employee's charges. */
    private static TraceLine furniture(NodeContext ctx, EmployerAccommodation accommodation, Furniture furniture,
            int sequence) {
        TemporalContext t = ctx.inputs().temporal();
        RuleRow rule = ctx.ruleOf(RuleSetIds.RS_PERQ_ACCOM_FURNITURE);
        RuleVersion version = TemporallyApportionedCap.governingVersion(rule, t);
        Money charged = furniture.cost()
                .map(cost -> BY_DAYS_OCCUPIED.apportion(TemporallyApportionedCap.apply(rule, t,
                        new CapContext(Map.of(FURNITURE_COST, cost))).value(), t.taxYear(), accommodation.occupied()))
                .orElseGet(() -> furniture.hireCharges().orElseThrow());
        Money value = charged.minus(furniture.employeeCharges()).floorAtZero();
        return TraceLine.of(sequence, NodeIds.SALARY_PERQUISITES, ctx.ruleIdOf(RuleSetIds.RS_PERQ_ACCOM_FURNITURE), StepKind.INCOME,
                        MoneyValue.of(charged), MoneyValue.of(value), version.provenance())
                .withLimitedBy((furniture.cost().isPresent() ? "ten per cent a year of the cost" : "hire charges")
                        + ", less the employee's charges " + furniture.employeeCharges());
    }

    /**
     * The accommodation "salary" for the period: the base less the allowances the exemptions allowed
     * (r.15(8)(k)(iii)), apportioned by days. Every exemption so far is of an allowance the base counts, so the
     * exemptions' total is what the base must lose.
     */
    private static Money salaryFor(NodeContext ctx, EmployerAccommodation accommodation, TemporalContext t) {
        if (accommodation.employment() >= ctx.inputs().entered().employments().size()) {
            throw new IllegalArgumentException("The accommodation names employment " + accommodation.employment()
                    + ", but " + ctx.inputs().entered().employments().size() + " were entered");
        }
        Money base = ctx.get(NodeIds.SALARY_BASE, SalaryBases.class)
                .of(ctx.pack().ruleSet(RuleSetIds.RS_SAL_SALARY_FOR_ACCOM), accommodation.employment());
        Money exempt = ctx.get(NodeIds.SALARY_EXEMPTIONS, MoneyValue.class).asMoney();
        return BY_DAYS_OCCUPIED.apportion(base.minus(exempt).floorAtZero(), t.taxYear(), accommodation.occupied());
    }

    private static void requireWithinYear(DateRange occupied, TemporalContext t) {
        if (!occupied.liesWithin(DateRange.between(t.taxYearStart(), t.taxYearEnd()))) {
            throw new IllegalArgumentException("Accommodation occupied " + occupied + " runs outside the tax year "
                    + t.taxYear().label() + "; enter the part of the period within the year");
        }
    }

    /** Refuses an entry naming an employment that was not entered. Shared by the perquisite helpers. */
    static void requireEnteredEmployment(NodeContext ctx, int employment, String what) {
        int entered = ctx.inputs().entered().employments().size();
        if (employment >= entered) {
            throw new IllegalArgumentException("The " + what + " names employment " + employment + ", but " + entered
                    + " were entered");
        }
    }
}
