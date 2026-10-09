package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.Tenancy;
import com.thiru.wealthlens.taxengine.pipeline.ComponentSet;
import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.SalaryBases;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapResult;
import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.definition.HeadTotal;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The exemptions taken out of salary before its income is struck — house rent allowance (Sch. III Table Sl. 11
 * with Rules 2026 r.279; 1961 s.10(13A) with Rules 1962 r.2A; D-099) and the leave travel concession (Sl. 8 with
 * r.278; s.10(5) with r.2B; D-100, in {@link LeaveTravelExemption}) and the salary allowances (Sl. 12–13 with
 * r.280; s.10(14) with r.2BB; D-101, D-102, in {@link AllowanceExemption}).
 *
 * <p>Per period of occupation, never on annual figures ({@code DR 13} R-11): each rented home the taxpayer
 * occupied gets its own least-of — the allowance received for the period, the rent paid for it in excess of
 * one-tenth of salary for it, and its location's percentage of that salary — and its own line naming the limb
 * that bound. The allowance and the salary are entered for the year and apportioned to each period by days
 * (r.279(2)(a): the relevant period is the period occupied). A home the taxpayer owns, or pays no rent for, gets
 * a nil line (Sl. 11(c), (d)). Where the rule does not apply — the new regime — each home entered gets a nil line
 * naming the regime, since a claim refused in silence reads as a claim forgotten ({@code DR 08} T3-4b; D-104).
 *
 * <p>With more than one employment and an exemption claimed, the node refuses: an employment carries no period yet,
 * so an annual allowance cannot be matched to the months each employer paid it, and a pro-rata guess is a plausible
 * wrong figure.
 */
public final class ExemptionNode implements ComputationNode {

    private static final BaseId HRA_RECEIVED = new BaseId("HRA_RECEIVED");
    private static final BaseId RENT_PAID = new BaseId("RENT_PAID");
    private static final BaseId SALARY = new BaseId("SALARY");
    private static final BaseId LOCATION = new BaseId("LOCATION");
    private static final ProrationBasis BY_DAYS_OCCUPIED = new Days();

    @Override
    public NodeId produces() {
        return NodeIds.SALARY_EXEMPTIONS;
    }

    @Override
    public Set<NodeId> requires() {
        return Set.of(NodeIds.INPUTS_COMPONENTS, NodeIds.SALARY_BASE);
    }

    @Override
    public Set<RuleId> rules() {
        Set<RuleId> rules = new HashSet<>(Set.of(RuleIds.SAL_HOUSE_RENT_ALLOWANCE, RuleIds.SAL_LEAVE_TRAVEL_CONCESSION,
                RuleIds.SAL_LEAVE_TRAVEL_DISTANCE_RATE, RuleIds.SAL_LEAVE_TRAVEL_JOURNEYS));
        rules.addAll(AllowanceExemption.rules());
        return Set.copyOf(rules);
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        List<Employment> employments = ctx.inputs().entered().employments();
        boolean claimed = !ctx.inputs().entered().tenancies().isEmpty()
                || !ctx.inputs().entered().leaveTravel().isEmpty()
                || !ctx.inputs().entered().allowanceUses().isEmpty()
                || ctx.inputs().entered().disabledTransport().isPresent();
        if (claimed && employments.size() > 1) {
            throw new IllegalStateException("An exemption is claimed with " + employments.size() + " employments,"
                    + " and an employment carries no period yet, so an annual allowance cannot be matched to the"
                    + " months each employer paid it; enter one employment until employment periods exist (D-099)");
        }
        List<TraceLine> trace = new ArrayList<>(houseRentAllowance(ctx, employments));
        trace.addAll(LeaveTravelExemption.lines(ctx, trace.size() + 1));
        trace.addAll(AllowanceExemption.lines(ctx, trace.size() + 1));
        Money exempt = trace.stream().map(line -> line.out().asMoney()).reduce(Money.ZERO, Money::plus);
        return trace.isEmpty() ? NodeResult.untraced(MoneyValue.of(Money.ZERO))
                : new NodeResult(MoneyValue.of(exempt), trace);
    }

    /** HRA's lines, one per tenancy; none where the rule does not apply or no home is entered. */
    private static List<TraceLine> houseRentAllowance(NodeContext ctx, List<Employment> employments) {
        TemporalContext t = ctx.inputs().temporal();
        RuleRow rule = ctx.rule(RuleIds.SAL_HOUSE_RENT_ALLOWANCE);
        RuleVersion version = TemporallyApportionedCap.governingVersion(rule, t);
        List<Tenancy> tenancies = ctx.inputs().entered().tenancies();
        if (tenancies.isEmpty()) {
            return List.of();
        }
        Money allowanceForYear = HeadTotalLines.of(ctx, ctx.get(NodeIds.INPUTS_COMPONENTS, ComponentSet.class),
                new HeadTotal(HeadCode.SALARIES), RuleIds.SAL_HOUSE_RENT_ALLOWANCE).total();
        if (!ctx.inRegime(RuleIds.SAL_HOUSE_RENT_ALLOWANCE, version).applies()) {
            List<TraceLine> nil = new ArrayList<>();
            for (Tenancy tenancy : tenancies) {
                requireWithinYear(tenancy.occupied(), t);
                Money allowance = BY_DAYS_OCCUPIED.apportion(allowanceForYear, t.taxYear(), tenancy.occupied());
                nil.add(TraceLine.of(nil.size() + 1, NodeIds.SALARY_EXEMPTIONS, RuleIds.SAL_HOUSE_RENT_ALLOWANCE,
                                StepKind.EXEMPTION, MoneyValue.of(allowance), MoneyValue.of(Money.ZERO),
                                version.provenance())
                        .withLimitedBy(ctx.inRegime(RuleIds.SAL_HOUSE_RENT_ALLOWANCE, version).whyNot()));
            }
            return nil;
        }
        Money salaryForYear = employments.isEmpty() ? Money.ZERO
                : ctx.get(NodeIds.SALARY_BASE, SalaryBases.class).of(RuleIds.SAL_SALARY_FOR_HRA, 0);
        List<TraceLine> trace = new ArrayList<>();
        for (Tenancy tenancy : tenancies) {
            trace.add(period(rule, version, t, tenancy, allowanceForYear, salaryForYear, trace.size() + 1));
        }
        return trace;
    }

    /** One period of occupation: its least-of, or nil with the reason where the home is not rented. */
    private static TraceLine period(RuleRow rule, RuleVersion version, TemporalContext t, Tenancy tenancy,
            Money allowanceForYear, Money salaryForYear, int sequence) {
        TaxYearId year = t.taxYear();
        requireWithinYear(tenancy.occupied(), t);
        Money allowance = BY_DAYS_OCCUPIED.apportion(allowanceForYear, year, tenancy.occupied());
        if (tenancy.ownedByAssessee() || !tenancy.rentPaid().isGreaterThan(Money.ZERO)) {
            return TraceLine.of(sequence, NodeIds.SALARY_EXEMPTIONS, RuleIds.SAL_HOUSE_RENT_ALLOWANCE,
                    StepKind.EXEMPTION, MoneyValue.of(allowance), MoneyValue.of(Money.ZERO), version.provenance())
                    .withLimitedBy("not rented: the home is owned or no rent was paid (Sch. III Table Sl. 11(c), (d))");
        }
        Money salary = BY_DAYS_OCCUPIED.apportion(salaryForYear, year, tenancy.occupied());
        CapResult exempt = TemporallyApportionedCap.apply(rule, t, new CapContext(
                Map.of(HRA_RECEIVED, allowance, RENT_PAID, tenancy.rentPaid(), SALARY, salary),
                Map.of(), Map.of(), Map.of(LOCATION, tenancy.location())));
        return TraceLine.of(sequence, NodeIds.SALARY_EXEMPTIONS, RuleIds.SAL_HOUSE_RENT_ALLOWANCE,
                        StepKind.EXEMPTION, MoneyValue.of(allowance), MoneyValue.of(exempt.value()),
                        version.provenance())
                .withLimitedBy(exempt.limitedBy());
    }

    /** Rent is entered for the window occupied, so a window the year does not contain cannot be split. */
    private static void requireWithinYear(DateRange occupied, TemporalContext t) {
        if (!occupied.liesWithin(DateRange.between(t.taxYearStart(), t.taxYearEnd()))) {
            throw new IllegalArgumentException("A tenancy occupied " + occupied + " runs outside the tax year "
                    + t.taxYear().label() + "; enter the part of it within the year, with the rent paid for that part");
        }
    }
}
