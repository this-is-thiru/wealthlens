package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.LeaveAtRetirement;
import com.thiru.wealthlens.taxengine.input.MonthOfPay;
import com.thiru.wealthlens.taxengine.input.RetirementEntry;
import com.thiru.wealthlens.taxengine.input.RetirementPayment;
import com.thiru.wealthlens.taxengine.input.Separation;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.component.ComponentDefinition;
import com.thiru.wealthlens.taxengine.rules.component.SalaryBaseInclusion;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The retirement limits the Central Government sets by notification, for {@link RetirementNode} (S2 s.19(1) Table
 * Sl. 6, 14 and s.19(2)(a), (b), (f); 1961 s.10(10)(iii), (10AA)(ii) and their provisos; D-106).
 *
 * <p>Other gratuity is measured on half a month's salary for each completed year, the salary averaged over the ten
 * months before the event's month by {@code SAL-SALARY-FOR-GRATUITY}'s own inclusions, so DA counts only where the
 * terms provide. Other leave encashment reads the employer's figures. Each notification covers events from a day —
 * the version's {@code introducedBy.effectiveFrom} — and an earlier event is refused, its limit being another
 * notification. Across employers and years each is capped at A − B, the notified limit less what earlier years
 * allowed.
 */
final class NotifiedRetirementLimits {

    private static final BaseId AVERAGE_SALARY_BY_COMPLETED_YEARS = new BaseId("AVERAGE_SALARY_BY_COMPLETED_YEARS");
    private static final BaseId LEAVE_SALARY_AT_CREDIT = new BaseId("LEAVE_SALARY_AT_CREDIT");
    private static final BaseId AVERAGE_MONTHLY_SALARY = new BaseId("AVERAGE_MONTHLY_SALARY");
    private static final int MONTHS_AVERAGED = 10;

    private static final Map<RetirementEntry, RuleId> AGGREGATE_FOR_ENTRY = Map.of(
            RetirementEntry.OTHER_GRATUITY, RuleIds.RET_GRATUITY_AGGREGATE,
            RetirementEntry.OTHER_LEAVE_ENCASHMENT, RuleIds.RET_LEAVE_ENCASHMENT_AGGREGATE);

    private NotifiedRetirementLimits() {
    }

    static List<RuleId> aggregateRules() {
        return List.copyOf(AGGREGATE_FOR_ENTRY.values());
    }

    static boolean isNotified(RetirementEntry entry) {
        return AGGREGATE_FOR_ENTRY.containsKey(entry);
    }

    /** Refuses an employment's gratuity and leave encashment that name different days: one employment ends once. */
    static void requireOneDay(List<RetirementPayment> payments) {
        payments.stream().map(RetirementPayment::employment).distinct().forEach(employment -> {
            List<RetirementPayment> its = payments.stream().filter(payment -> payment.employment() == employment)
                    .toList();
            Optional<LocalDate> separated = its.stream()
                    .flatMap(payment -> payment.separation().stream()).map(Separation::separatedOn).findFirst();
            Optional<LocalDate> retired = its.stream()
                    .flatMap(payment -> payment.leave().stream()).map(LeaveAtRetirement::retiredOn).findFirst();
            if (separated.isPresent() && retired.isPresent() && !separated.equals(retired)) {
                throw new IllegalArgumentException("Employment " + employment + "'s gratuity is entered for a"
                        + " separation on " + separated.get() + " and its leave encashment for a retirement on "
                        + retired.get() + "; one employment ends once");
            }
        });
    }

    /** The amounts the entry's least-of reads, after refusing an event its notification does not cover. */
    static CapContext context(NodeContext ctx, RetirementPayment payment,
            RuleVersion version, Map<BaseId, Money> received) {
        Map<BaseId, Money> bases = new HashMap<>(received);
        if (payment.entry() == RetirementEntry.OTHER_GRATUITY) {
            Separation separation = payment.separation().orElseThrow();
            requireCovered(version, separation.separatedOn(), payment);
            Money average = tenMonthAverage(ctx, separation, ctx.inputs().entered().employments().get(payment.employment()));
            bases.put(AVERAGE_SALARY_BY_COMPLETED_YEARS, average.times(separation.completedYears()));
        } else {
            LeaveAtRetirement leave = payment.leave().orElseThrow();
            requireCovered(version, leave.retiredOn(), payment);
            bases.put(LEAVE_SALARY_AT_CREDIT, leave.leaveSalaryAtCredit());
            bases.put(AVERAGE_MONTHLY_SALARY, leave.averageMonthlySalary());
        }
        return new CapContext(Map.copyOf(bases));
    }

    /**
     * The line as the aggregate leaves it: unchanged where what A − B leaves is not below it, else a line citing the
     * aggregate rule, with what the entry alone allowed as its input and what is left as its output. A − B caps the
     * year's total from every employer (S2 s.19(2)(a), (f); 1961 provisos), so this year's earlier payments of the
     * entry, in the order entered, come off it first; which payment absorbs the cut changes no tax (D-225).
     */
    static TraceLine capped(NodeContext ctx, RetirementEntry entry, Money allowedThisYear, TraceLine line) {
        RuleId aggregateId = AGGREGATE_FOR_ENTRY.get(entry);
        RuleVersion version = TemporallyApportionedCap.governingVersion(ctx.rule(aggregateId), ctx.inputs().temporal());
        Money limit = TemporallyApportionedCap.apply(ctx.rule(aggregateId), ctx.inputs().temporal(),
                new CapContext(Map.of())).value();
        Money allowedBefore = entry == RetirementEntry.OTHER_GRATUITY
                ? ctx.inputs().ledgers().gratuityAlreadyAllowed()
                : ctx.inputs().ledgers().leaveEncashmentAlreadyAllowed();
        Money remaining = limit.minus(allowedBefore).floorAtZero().minus(allowedThisYear).floorAtZero();
        Money allowed = line.out().asMoney();
        if (allowed.isLessThanOrEqual(remaining)) {
            return line;
        }
        String why = "notified limit less the " + allowedBefore + " allowed in earlier years";
        if (allowedThisYear.isGreaterThan(Money.ZERO)) {
            why += " and the " + allowedThisYear + " allowed on this year's earlier payments, in the order entered (the"
                    + " split changes no tax; D-225)";
        }
        return TraceLine.of(line.sequence(), line.node(), aggregateId, line.kind(), MoneyValue.of(allowed),
                        MoneyValue.of(remaining), version.provenance())
                .withLimitedBy(why);
    }

    private static void requireCovered(RuleVersion version, LocalDate event, RetirementPayment payment) {
        LocalDate covered = version.introducedBy().effectiveFrom();
        if (event.isBefore(covered)) {
            throw new IllegalArgumentException(payment.component() + ": the event on " + event + " precedes "
                    + covered + ", from which " + version.introducedBy().gazetteRef() + " sets the limit; an earlier"
                    + " event's limit is another notification, which is not built");
        }
    }

    /** "Average salary for ten months immediately preceding the month when any such event occurs". */
    private static Money tenMonthAverage(NodeContext ctx, Separation separation, Employment employment) {
        Money total = Money.ZERO;
        for (MonthOfPay month : separation.monthsBefore()) {
            for (Map.Entry<ComponentCode, Money> line : month.amounts().entrySet()) {
                if (inGratuityBase(ctx, line.getKey(), employment)) {
                    total = total.plus(line.getValue());
                }
            }
        }
        return total.apportioned(1, MONTHS_AVERAGED);
    }

    private static boolean inGratuityBase(NodeContext ctx, ComponentCode code, Employment employment) {
        ComponentDefinition definition = ctx.pack().components().get(code);
        if (definition == null) {
            throw new IllegalArgumentException(code + " in the ten months before the separation is not a known pay"
                    + " component");
        }
        return definition.salaryBaseFor().stream()
                .filter(inclusion -> inclusion.rule().equals(RuleIds.SAL_SALARY_FOR_GRATUITY))
                .findFirst()
                .map(SalaryBaseInclusion::onlyWhereTermsProvide)
                .map(onlyWhereTerms -> !onlyWhereTerms || employment.termsIncludeDearnessAllowance())
                .orElse(false);
    }
}
