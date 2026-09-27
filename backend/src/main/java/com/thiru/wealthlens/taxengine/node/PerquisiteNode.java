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
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The value of perquisites, which is salary in every regime (S2 s.16, s.17(1); 1961 s.17(1)(iv), (2); {@code DR 06}
 * G1.1 step 3; D-111). Built so far: residential accommodation (S4 r.15(2) Table I; Rules 1962 r.3(1)).
 *
 * <p>Each accommodation is valued on its Table row — the licence fee, a percentage of salary by the city's
 * population, the lower of the lease rental or hotel charges and a percentage of salary — "as reduced by the rent …
 * actually paid", plus its furniture. The "salary" is the accommodation base less the allowances the exemptions
 * allowed (r.15(8)(k)(iii)), which is why this node runs after {@code salary.exemptions}; it is apportioned to the
 * period occupied by days, as HRA's is. Accommodation continued from an earlier year is refused until the Cost
 * Inflation Index its cap reads is in the pack (D-111 C5a). A perquisite is provided, not paid: its value is non-cash
 * income, never take-home.
 */
public final class PerquisiteNode implements ComputationNode {

    private static final BaseId SALARY = new BaseId("SALARY");
    private static final BaseId EMPLOYER_COST = new BaseId("EMPLOYER_COST");
    private static final BaseId LOCATION = new BaseId("LOCATION");
    private static final BaseId FURNITURE_COST = new BaseId("FURNITURE_COST");
    private static final ProrationBasis BY_DAYS_OCCUPIED = new Days();

    private static final Map<AccommodationProvider, RuleId> RULE_FOR_PROVIDER = providerRules();

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
        return Set.of(NodeIds.SALARY_BASE, NodeIds.SALARY_EXEMPTIONS);
    }

    @Override
    public Set<RuleId> rules() {
        return Set.of(RuleIds.PERQ_ACCOM_GOVERNMENT, RuleIds.PERQ_ACCOM_OWNED, RuleIds.PERQ_ACCOM_LEASED,
                RuleIds.PERQ_ACCOM_HOTEL, RuleIds.PERQ_ACCOM_FURNITURE);
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
        Money value = trace.stream().map(line -> line.out().asMoney()).reduce(Money.ZERO, Money::plus);
        return trace.isEmpty() ? NodeResult.untraced(MoneyValue.of(Money.ZERO))
                : new NodeResult(MoneyValue.of(value), trace);
    }

    /** One accommodation's Table I value, less the rent the employee paid. */
    private static TraceLine accommodation(NodeContext ctx, EmployerAccommodation accommodation, int sequence) {
        TemporalContext t = ctx.inputs().temporal();
        requireWithinYear(accommodation.occupied(), t);
        requireNoIndexCap(accommodation, t.taxYear());
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
        return TraceLine.of(sequence, NodeIds.SALARY_PERQUISITES, ruleId, StepKind.INCOME, MoneyValue.of(salary),
                        MoneyValue.of(value), version.provenance())
                .withLimitedBy(valued.limitedBy() + ", less the rent paid " + accommodation.rentPaid());
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

    /** Refuses accommodation continued from an earlier year where the index cap applies, until the index is read. */
    private static void requireNoIndexCap(EmployerAccommodation accommodation, TaxYearId year) {
        boolean capped = accommodation.provider() == AccommodationProvider.EMPLOYER_OWNED
                || accommodation.provider() == AccommodationProvider.EMPLOYER_LEASED;
        if (capped && accommodation.firstProvidedIn().startYear() < year.startYear()) {
            throw new IllegalArgumentException("Accommodation first provided in " + accommodation.firstProvidedIn()
                    + " is capped by the Cost Inflation Index (r.15(2)(d); r.3(1) third proviso), which is not yet in"
                    + " the rule pack; it cannot be valued until it is (D-111)");
        }
    }

    private static void requireWithinYear(DateRange occupied, TemporalContext t) {
        if (!occupied.liesWithin(DateRange.between(t.taxYearStart(), t.taxYearEnd()))) {
            throw new IllegalArgumentException("Accommodation occupied " + occupied + " runs outside the tax year "
                    + t.taxYear().label() + "; enter the part of the period within the year");
        }
    }
}
