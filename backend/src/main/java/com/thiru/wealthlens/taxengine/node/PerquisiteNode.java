package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.AccommodationProvider;
import com.thiru.wealthlens.taxengine.input.EmployerAccommodation;
import com.thiru.wealthlens.taxengine.input.Furniture;
import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.SalaryBases;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapResult;
import com.thiru.wealthlens.taxengine.rules.definition.CommutedPensionShare;
import com.thiru.wealthlens.taxengine.rules.definition.DisabledTransportLimit;
import com.thiru.wealthlens.taxengine.rules.definition.HeadTotal;
import com.thiru.wealthlens.taxengine.rules.definition.IndexByYear;
import com.thiru.wealthlens.taxengine.rules.definition.JourneysPerCalendarBlock;
import com.thiru.wealthlens.taxengine.rules.definition.SalaryBase;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The value of perquisites, which is salary in every regime (S2 s.16, s.17(1); 1961 s.17(1)(iv), (2); {@code DR 06}
 * G1.1 step 3; D-111). Built so far: residential accommodation (S4 r.15(2) Table I; Rules 1962 r.3(1)) and, through
 * {@link MotorCarPerquisite}, a vehicle the employer provides or runs (Table II; r.3(2); D-113) — which reads
 * {@code salary.monetaryincome} for the specified-employee test; meals and gifts through {@link MealPerquisite} and
 * {@link GiftPerquisite} (Table IV Sl. 3–4; r.3(7)(iii)–(iv); D-117).
 *
 * <p>Each accommodation is valued on its Table row — the licence fee, a percentage of salary by the city's
 * population, the lower of the lease rental or hotel charges and a percentage of salary — "as reduced by the rent …
 * actually paid", plus its furniture. The "salary" is the accommodation base less the allowances the exemptions
 * allowed (r.15(8)(k)(iii)), which is why this node runs after {@code salary.exemptions}; it is apportioned to the
 * period occupied by days, as HRA's is. Owned or leased accommodation continued from an earlier year is capped at the
 * first year's amount scaled by the Cost Inflation Index (D-115). A perquisite is provided, not paid: its value is non-cash
 * income, never take-home.
 */
public final class PerquisiteNode implements ComputationNode {

    private static final BaseId SALARY = new BaseId("SALARY");
    private static final BaseId EMPLOYER_COST = new BaseId("EMPLOYER_COST");
    private static final BaseId LOCATION = new BaseId("LOCATION");
    private static final BaseId FURNITURE_COST = new BaseId("FURNITURE_COST");
    private static final BaseId SCALED_FIRST_YEAR_VALUE = new BaseId("SCALED_FIRST_YEAR_VALUE");
    private static final ProrationBasis BY_DAYS_OCCUPIED = new Days();

    private static final Map<AccommodationProvider, RuleId> RULE_FOR_PROVIDER = providerRules();

    private static final Set<RuleId> RULES = rulesRead();

    private static Set<RuleId> rulesRead() {
        Set<RuleId> rules = new HashSet<>(MotorCarPerquisite.RULES);
        rules.addAll(MealPerquisite.RULES);
        rules.add(RuleIds.PERQ_GIFT);
        rules.addAll(Set.of(RuleIds.PERQ_ACCOM_GOVERNMENT, RuleIds.PERQ_ACCOM_OWNED, RuleIds.PERQ_ACCOM_LEASED,
                RuleIds.PERQ_ACCOM_HOTEL, RuleIds.PERQ_ACCOM_FURNITURE, RuleIds.PERQ_ACCOM_CONTINUED,
                RuleIds.RATE_COST_INFLATION_INDEX));
        return Set.copyOf(rules);
    }

    private static Map<AccommodationProvider, RuleId> providerRules() {
        Map<AccommodationProvider, RuleId> rules = new EnumMap<>(AccommodationProvider.class);
        rules.put(AccommodationProvider.GOVERNMENT, RuleIds.PERQ_ACCOM_GOVERNMENT);
        rules.put(AccommodationProvider.EMPLOYER_OWNED, RuleIds.PERQ_ACCOM_OWNED);
        rules.put(AccommodationProvider.EMPLOYER_LEASED, RuleIds.PERQ_ACCOM_LEASED);
        rules.put(AccommodationProvider.HOTEL, RuleIds.PERQ_ACCOM_HOTEL);
        return Map.copyOf(rules);
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
    public Set<RuleId> rules() {
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
        Money value = trace.stream().map(line -> line.out().asMoney()).reduce(Money.ZERO, Money::plus);
        return trace.isEmpty() ? NodeResult.untraced(MoneyValue.of(Money.ZERO))
                : new NodeResult(MoneyValue.of(value), trace);
    }

    /** One accommodation's Table I value, less the rent the employee paid. */
    private static TraceLine accommodation(NodeContext ctx, EmployerAccommodation accommodation, int sequence) {
        TemporalContext t = ctx.inputs().temporal();
        requireWithinYear(accommodation.occupied(), t);
        RuleId ruleId = RULE_FOR_PROVIDER.get(accommodation.provider());
        RuleRow rule = ctx.rule(ruleId);
        RuleVersion version = TemporallyApportionedCap.governingVersion(rule, t);
        Money salary = salaryFor(ctx, accommodation, t);
        Map<BaseId, Money> bases = new HashMap<>(Map.of(SALARY, salary));
        accommodation.employerCost().ifPresent(cost -> bases.put(EMPLOYER_COST, cost));
        Map<BaseId, String> locations = accommodation.band()
                .map(band -> Map.of(LOCATION, band.name()))
                .orElse(Map.of());
        CapResult valued = TemporallyApportionedCap.apply(rule, t,
                new CapContext(Map.copyOf(bases), Map.of(), Map.of(), locations));
        Money value = valued.value().minus(accommodation.rentPaid()).floorAtZero();
        String bound = valued.limitedBy() + ", less the rent paid " + accommodation.rentPaid();
        if (isContinued(accommodation, t.taxYear())) {
            Money cap = continuedCap(ctx, accommodation, t);
            if (cap.isLessThan(value)) {
                value = cap;
                bound += "; capped at the first year's amount scaled by the Cost Inflation Index, " + cap;
            }
        }
        return TraceLine.of(sequence, NodeIds.SALARY_PERQUISITES, ruleId, StepKind.INCOME, MoneyValue.of(salary),
                        MoneyValue.of(value), version.provenance())
                .withLimitedBy(bound);
    }

    /** Owned or leased accommodation provided to the employee before this tax year (r.15(2)(d); r.3(1) proviso). */
    private static boolean isContinued(EmployerAccommodation accommodation, TaxYearId year) {
        boolean ownedOrLeased = accommodation.provider() == AccommodationProvider.EMPLOYER_OWNED
                || accommodation.provider() == AccommodationProvider.EMPLOYER_LEASED;
        return ownedOrLeased && accommodation.firstProvidedIn().startYear() < year.startYear();
    }

    /**
     * The first year's amount, scaled by this year's index over the first year's (D-115). A first year the index
     * table does not hold — any before 2023-24, the first tax year the Rules name — is refused: which year's index
     * then divides is OQ-11.
     */
    private static Money continuedCap(NodeContext ctx, EmployerAccommodation accommodation, TemporalContext t) {
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
        return TemporallyApportionedCap.apply(ctx.rule(RuleIds.PERQ_ACCOM_CONTINUED), t,
                CapContext.of(SCALED_FIRST_YEAR_VALUE, scaled)).value();
    }

    private static IndexByYear costInflationIndex(NodeContext ctx, TemporalContext t) {
        RuleId ruleId = RuleIds.RATE_COST_INFLATION_INDEX;
        return switch (ctx.rule(ruleId).versionAt(t).definition().orElseThrow(() -> new IllegalStateException(
                ruleId + " carries no index table"))) {
            case IndexByYear table -> table;
            case HeadTotal headTotal -> throw new IllegalStateException(ruleId + " defines a head's total, not an index");
            case SalaryBase base -> throw new IllegalStateException(ruleId + " defines a salary base, not an index");
            case JourneysPerCalendarBlock journeys -> throw new IllegalStateException(
                    ruleId + " defines journeys per block, not an index");
            case DisabledTransportLimit transport -> throw new IllegalStateException(
                    ruleId + " defines a transport limit, not an index");
            case CommutedPensionShare share -> throw new IllegalStateException(
                    ruleId + " defines a share of a commuted pension, not an index");
        };
    }

    /** Furnished: ten per cent a year of the cost for the period, or the hire charges; less the employee's charges. */
    private static TraceLine furniture(NodeContext ctx, EmployerAccommodation accommodation, Furniture furniture,
            int sequence) {
        TemporalContext t = ctx.inputs().temporal();
        RuleRow rule = ctx.rule(RuleIds.PERQ_ACCOM_FURNITURE);
        RuleVersion version = TemporallyApportionedCap.governingVersion(rule, t);
        Money charged = furniture.cost()
                .map(cost -> BY_DAYS_OCCUPIED.apportion(TemporallyApportionedCap.apply(rule, t,
                        new CapContext(Map.of(FURNITURE_COST, cost))).value(), t.taxYear(), accommodation.occupied()))
                .orElseGet(() -> furniture.hireCharges().orElseThrow());
        Money value = charged.minus(furniture.employeeCharges()).floorAtZero();
        return TraceLine.of(sequence, NodeIds.SALARY_PERQUISITES, RuleIds.PERQ_ACCOM_FURNITURE, StepKind.INCOME,
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
                .of(RuleIds.SAL_SALARY_FOR_ACCOM, accommodation.employment());
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
