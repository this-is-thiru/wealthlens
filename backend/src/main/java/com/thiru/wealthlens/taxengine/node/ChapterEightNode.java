package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.EmployerFund;
import com.thiru.wealthlens.taxengine.input.EmployerFundContribution;
import com.thiru.wealthlens.taxengine.input.EmployerKind;
import com.thiru.wealthlens.taxengine.input.Individual;
import com.thiru.wealthlens.taxengine.input.LifeInsurancePolicy;
import com.thiru.wealthlens.taxengine.input.QualifyingPayment;
import com.thiru.wealthlens.taxengine.pipeline.ComponentSet;
import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.SalaryBases;
import com.thiru.wealthlens.taxengine.rules.cap.AggregateOutcome;
import com.thiru.wealthlens.taxengine.rules.cap.AggregateWithCeiling;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.component.ComponentDefinition;
import com.thiru.wealthlens.taxengine.rules.component.ComponentKind;
import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.definition.CommutedPensionShare;
import com.thiru.wealthlens.taxengine.rules.definition.DayWindow;
import com.thiru.wealthlens.taxengine.rules.definition.DisabledTransportLimit;
import com.thiru.wealthlens.taxengine.rules.definition.ExcludedAssets;
import com.thiru.wealthlens.taxengine.rules.definition.HeadTotal;
import com.thiru.wealthlens.taxengine.rules.definition.IndexByYear;
import com.thiru.wealthlens.taxengine.rules.definition.JourneysPerCalendarBlock;
import com.thiru.wealthlens.taxengine.rules.definition.PremiumShareBands;
import com.thiru.wealthlens.taxengine.rules.definition.SalaryBase;
import com.thiru.wealthlens.taxengine.rules.generated.ComponentCodes;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The Chapter VIII deductions built so far: the s.123 family and the pension scheme (S2 ss.122–124, Schedule XV;
 * {@code DR 06} G2.2; D-130, D-132).
 *
 * <p>In order, because each reads the one before: each life policy's premium within its share of the sum assured
 * (Schedule XV para 2), as a measure; the Schedule XV sums under one ceiling, the employee's pension
 * contribution sub-capped first; what that sub-cap left, with a minor's deposit, under s.124(3)–(4) — never the same
 * amount twice (s.124(5), (10)); the employer's pension contribution by employer and regime (s.124(1)–(2)); then all
 * of it within gross total income (s.122(2)). A row the regime excludes, with something entered for it, gives a nil
 * line naming the regime. TY 2025-26 has none of these rows yet (D-129 C2a), so anything entered there is refused.
 * Then s.126, health insurance and medical expenditure ({@link HealthInsuranceDeduction}; D-136); and s.154, s.127
 * and s.128 ({@link DisabilityDeduction}; D-138). The other deductions are later items (W-075a3, W-075c2–c6).
 */
public final class ChapterEightNode implements ComputationNode {

    private static final BaseId SALARY = new BaseId("SALARY");
    private static final BaseId GROSS_TOTAL_INCOME = new BaseId("GROSS_TOTAL_INCOME");
    private static final Set<RuleId> PAYMENT_ROWS = Set.of(RuleIds.DED_SCHEDULE_XV, RuleIds.DED_NPS_OWN);

    @Override
    public NodeId produces() {
        return NodeIds.CHAPTERVIII;
    }

    @Override
    public Set<NodeId> requires() {
        return Set.of(NodeIds.GTI, NodeIds.INPUTS_COMPONENTS, NodeIds.SALARY_BASE);
    }

    @Override
    public Set<RuleId> rules() {
        Set<RuleId> rules = new HashSet<>(Set.of(RuleIds.DED_SCHEDULE_XV, RuleIds.DED_NPS_OWN,
                RuleIds.DED_NPS_EMPLOYER_GOVT, RuleIds.DED_NPS_EMPLOYER_OTHER, RuleIds.DED_NPS_EMPLOYER_NEW_TAX_REGIME,
                RuleIds.DED_WITHIN_GROSS_TOTAL, RuleIds.DED_PREMIUM_SHARE));
        rules.addAll(HealthInsuranceDeduction.RULES);
        rules.addAll(DisabilityDeduction.RULES);
        return Set.copyOf(rules);
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        List<ComponentSet.Line> payLines = payLines(ctx);
        List<QualifyingPayment> payments = ctx.inputs().entered().qualifyingPayments();
        List<EmployerFundContribution> employerPension = ctx.inputs().entered().employerContributions().stream()
                .filter(contribution -> contribution.fund() == EmployerFund.NOTIFIED_PENSION_SCHEME)
                .toList();
        List<LifeInsurancePolicy> policies = ctx.inputs().entered().lifeInsurancePolicies();
        if (payLines.isEmpty() && payments.isEmpty() && employerPension.isEmpty() && policies.isEmpty()
                && ctx.inputs().entered().healthPayments().isEmpty()
                && ctx.inputs().entered().disabledDependantSupport().isEmpty()
                && ctx.inputs().entered().specifiedDiseaseTreatment().isEmpty()
                && !certifiedDisabled(ctx)) {
            return NodeResult.untraced(MoneyValue.of(Money.ZERO));
        }
        requireRowsThisYear(ctx);
        Map<ComponentCode, Money> paid = paid(ctx, payLines, payments);
        List<TraceLine> lines = new ArrayList<>();
        premiums(ctx, policies, paid, lines);
        Money residue = scheduleXv(ctx, paid, lines);
        ownPension(ctx, residue, paid.getOrDefault(ComponentCodes.NPS_MINOR_DEPOSIT, Money.ZERO), lines);
        employerPension(ctx, employerPension, lines);
        lines.addAll(HealthInsuranceDeduction.lines(ctx, lines.size() + 1));
        lines.addAll(DisabilityDeduction.lines(ctx, lines.size() + 1));
        return withinGrossTotal(ctx, lines);
    }

    /** Whether the assessee is certified disabled — s.154 applies with nothing entered. */
    private static boolean certifiedDisabled(NodeContext ctx) {
        return ctx.inputs().assessee() instanceof Individual individual && individual.disability().isPresent();
    }

    /** The pay lines that feed a Chapter VIII row — the employee's own contributions withheld from pay. */
    private static List<ComponentSet.Line> payLines(NodeContext ctx) {
        return ctx.get(NodeIds.INPUTS_COMPONENTS, ComponentSet.class).in(HeadCode.SALARIES).stream()
                .filter(line -> !Collections.disjoint(ctx.pack().components().get(line.code()).feedsInto(),
                        PAYMENT_ROWS))
                .toList();
    }

    private static void requireRowsThisYear(NodeContext ctx) {
        if (!ctx.pack().rules().containsKey(RuleIds.DED_SCHEDULE_XV)) {
            throw new IllegalArgumentException("The Chapter VIII deductions for " + ctx.inputs().taxYear().label()
                    + " are not in the rule pack yet (W-075d, D-129); what was entered for them cannot be computed"
                    + " for this year");
        }
    }

    /** Every amount paid, by code: the pay lines and the qualifying payments, each a live qualifying payment code. */
    private static Map<ComponentCode, Money> paid(NodeContext ctx, List<ComponentSet.Line> payLines,
            List<QualifyingPayment> payments) {
        Map<ComponentCode, Money> paid = new LinkedHashMap<>();
        payLines.forEach(line -> paid.merge(line.code(), line.amount(), Money::plus));
        for (QualifyingPayment payment : payments) {
            ComponentDefinition definition = ctx.pack().components().get(payment.code());
            if (payment.code().equals(ComponentCodes.LIFE_INSURANCE_PREMIUM)) {
                throw new IllegalArgumentException("A life insurance premium is entered with its policy — the sum"
                        + " assured, issue date and whom it covers decide how much counts (Schedule XV para 2; D-134)");
            }
            if (definition == null || !definition.lifecycle().isLiveIn(ctx.inputs().taxYear())
                    || definition.kind() != ComponentKind.QUALIFYING_PAYMENT) {
                throw new IllegalArgumentException(payment.code() + " is not a qualifying payment in the "
                        + ctx.inputs().taxYear().label() + " registry; a payment is entered under its Schedule XV"
                        + " or s.124 code (D-132)");
            }
            paid.merge(payment.code(), payment.amount(), Money::plus);
        }
        return paid;
    }

    /**
     * Schedule XV para 2: each policy's premium counts up to its band's share of the actual capital sum assured — a
     * measure line per policy; the qualifying total is the life insurance contribution to s.123. In a regime the row
     * does not apply in, the premiums go to s.123 as paid, for its nil line.
     */
    private static void premiums(NodeContext ctx, List<LifeInsurancePolicy> policies, Map<ComponentCode, Money> paid,
            List<TraceLine> lines) {
        if (policies.isEmpty()) {
            return;
        }
        RuleVersion version = version(ctx, RuleIds.DED_PREMIUM_SHARE);
        if (!version.regimes().appliesIn(ctx.regime().code())) {
            policies.forEach(policy -> paid.merge(ComponentCodes.LIFE_INSURANCE_PREMIUM, policy.premiumPaid(),
                    Money::plus));
            return;
        }
        PremiumShareBands bands = bands(version);
        for (LifeInsurancePolicy policy : policies) {
            PremiumShareBands.Band band = bands.bandFor(policy.issuedOn(), policy.coversDisabilityOrSpecifiedDisease());
            Money share = band.share().of(policy.actualCapitalSumAssured());
            Money counted = Money.min(policy.premiumPaid(), share);
            lines.add(TraceLine.of(lines.size() + 1, NodeIds.CHAPTERVIII, RuleIds.DED_PREMIUM_SHARE, StepKind.MEASURE,
                            MoneyValue.of(policy.premiumPaid()), MoneyValue.of(counted), version.provenance())
                    .withLimitedBy(band.share().asPercentLabel() + " of the sum assured " + policy.actualCapitalSumAssured()
                            + ", a policy issued " + policy.issuedOn()));
            paid.merge(ComponentCodes.LIFE_INSURANCE_PREMIUM, counted, Money::plus);
        }
    }

    private static PremiumShareBands bands(RuleVersion version) {
        RuleId ruleId = RuleIds.DED_PREMIUM_SHARE;
        return switch (version.definition().orElseThrow(
                () -> new IllegalStateException(ruleId + " carries no premium share bands"))) {
            case PremiumShareBands bands -> bands;
            case HeadTotal headTotal -> throw new IllegalStateException(ruleId + " defines a head's total");
            case SalaryBase base -> throw new IllegalStateException(ruleId + " defines a salary base");
            case JourneysPerCalendarBlock journeys -> throw new IllegalStateException(ruleId + " defines journeys");
            case DisabledTransportLimit transport -> throw new IllegalStateException(ruleId + " defines a transport limit");
            case CommutedPensionShare share -> throw new IllegalStateException(ruleId + " defines a pension share");
            case IndexByYear index -> throw new IllegalStateException(ruleId + " defines an index table");
            case ExcludedAssets excluded -> throw new IllegalStateException(ruleId + " defines excluded assets");
            case DayWindow window -> throw new IllegalStateException(ruleId + " defines a window of days");
        };
    }

    /** s.123: the Schedule XV sums under one ceiling; returns what the pension sub-cap left, for s.124(3). */
    private static Money scheduleXv(NodeContext ctx, Map<ComponentCode, Money> paid, List<TraceLine> lines) {
        RuleVersion version = version(ctx, RuleIds.DED_SCHEDULE_XV);
        AggregateWithCeiling cap = (AggregateWithCeiling) version.cap();
        Map<ComponentCode, Money> contributions = new LinkedHashMap<>();
        cap.contributors().forEach(code -> contributions.put(code, paid.getOrDefault(code, Money.ZERO)));
        Money entered = contributions.values().stream().reduce(Money.ZERO, Money::plus);
        if (!entered.isGreaterThan(Money.ZERO)) {
            return Money.ZERO;
        }
        if (!version.regimes().appliesIn(ctx.regime().code())) {
            lines.add(nilLine(ctx, lines, RuleIds.DED_SCHEDULE_XV, version, entered));
            return Money.ZERO;
        }
        SalaryBases bases = ctx.get(NodeIds.SALARY_BASE, SalaryBases.class);
        Money salary = Money.ZERO;
        for (int employment = 0; employment < ctx.inputs().entered().employments().size(); employment++) {
            salary = salary.plus(bases.of(RuleIds.SAL_SALARY_FOR_EMPLOYEE_PENSION, employment));
        }
        AggregateOutcome outcome = cap.applyDetailed(new CapContext(Map.of(SALARY, salary), contributions, Map.of()));
        lines.add(TraceLine.of(lines.size() + 1, NodeIds.CHAPTERVIII, RuleIds.DED_SCHEDULE_XV, StepKind.DEDUCTION,
                        MoneyValue.of(outcome.aggregate()), MoneyValue.of(outcome.allowed()), version.provenance())
                .withLimitedBy(outcome.limitedBy()));
        return outcome.subCapResidue().getOrDefault(ComponentCodes.EMPLOYEE_NPS, Money.ZERO);
    }

    /** s.124(3)–(4): the pension contribution s.123 did not allow, with a minor's deposit, under one ceiling. */
    private static void ownPension(NodeContext ctx, Money residue, Money minorDeposit, List<TraceLine> lines) {
        Money entered = residue.plus(minorDeposit);
        if (!entered.isGreaterThan(Money.ZERO)) {
            return;
        }
        RuleVersion version = version(ctx, RuleIds.DED_NPS_OWN);
        if (!version.regimes().appliesIn(ctx.regime().code())) {
            lines.add(nilLine(ctx, lines, RuleIds.DED_NPS_OWN, version, entered));
            return;
        }
        AggregateOutcome outcome = ((AggregateWithCeiling) version.cap()).applyDetailed(CapContext.ofContributions(
                Map.of(ComponentCodes.EMPLOYEE_NPS, residue, ComponentCodes.NPS_MINOR_DEPOSIT, minorDeposit)));
        lines.add(TraceLine.of(lines.size() + 1, NodeIds.CHAPTERVIII, RuleIds.DED_NPS_OWN, StepKind.DEDUCTION,
                        MoneyValue.of(entered), MoneyValue.of(outcome.allowed()), version.provenance())
                .withLimitedBy(outcome.limitedBy()));
    }

    /** s.124(1)–(2): the employer's pension contribution, up to the row's share of the employer-pension salary. */
    private static void employerPension(NodeContext ctx, List<EmployerFundContribution> contributions,
            List<TraceLine> lines) {
        if (contributions.isEmpty()) {
            return;
        }
        Set<Integer> employments = contributions.stream().map(EmployerFundContribution::employment)
                .collect(Collectors.toSet());
        if (employments.size() > 1) {
            throw new IllegalArgumentException("Employer pension contributions from " + employments.size()
                    + " employments: whether each is measured against that employer's salary or the year's is not"
                    + " settled (s.124(1); OQ-15), so it is refused rather than guessed (D-132)");
        }
        int employment = employments.iterator().next();
        PerquisiteNode.requireEnteredEmployment(ctx, employment, "employer pension contribution");
        Money contributed = contributions.stream().map(EmployerFundContribution::amount)
                .reduce(Money.ZERO, Money::plus);
        RuleId row = employerRow(ctx, employment);
        TemporalContext t = ctx.inputs().temporal();
        Money salary = ctx.get(NodeIds.SALARY_BASE, SalaryBases.class)
                .of(RuleIds.SAL_SALARY_FOR_EMPLOYER_PENSION, employment);
        Money limit = TemporallyApportionedCap.apply(ctx.rule(row), t, CapContext.of(SALARY, salary)).value();
        Money allowed = Money.min(contributed, limit);
        lines.add(TraceLine.of(lines.size() + 1, NodeIds.CHAPTERVIII, row, StepKind.DEDUCTION,
                        MoneyValue.of(contributed), MoneyValue.of(allowed), version(ctx, row).provenance())
                .withLimitedBy(allowed.isLessThan(contributed)
                        ? "the share of the employer-pension salary " + salary + ", " + limit
                        : "the contribution"));
    }

    /** A Government employer's row in every regime; any other employer's by the regime (s.124(1)(b), (2)). */
    private static RuleId employerRow(NodeContext ctx, int employment) {
        if (ctx.inputs().entered().employments().get(employment).employerKind() == EmployerKind.GOVERNMENT) {
            return RuleIds.DED_NPS_EMPLOYER_GOVT;
        }
        for (RuleId row : List.of(RuleIds.DED_NPS_EMPLOYER_OTHER, RuleIds.DED_NPS_EMPLOYER_NEW_TAX_REGIME)) {
            if (version(ctx, row).regimes().appliesIn(ctx.regime().code())) {
                return row;
            }
        }
        throw new IllegalStateException("No employer pension row applies in the " + ctx.regime().code().value()
                + " regime");
    }

    /** s.122(2): the deductions together never exceed gross total income; a line only where that binds. */
    private static NodeResult withinGrossTotal(NodeContext ctx, List<TraceLine> lines) {
        if (lines.isEmpty()) {
            return NodeResult.untraced(MoneyValue.of(Money.ZERO));
        }
        Money total = lines.stream()
                .filter(line -> line.kind() == StepKind.DEDUCTION)
                .map(line -> ((MoneyValue) line.out()).asMoney())
                .reduce(Money.ZERO, Money::plus);
        Money grossTotal = ctx.get(NodeIds.GTI, MoneyValue.class).asMoney().floorAtZero();
        Money limit = TemporallyApportionedCap.apply(ctx.rule(RuleIds.DED_WITHIN_GROSS_TOTAL), ctx.inputs().temporal(),
                CapContext.of(GROSS_TOTAL_INCOME, grossTotal)).value();
        if (!total.isGreaterThan(limit)) {
            return new NodeResult(MoneyValue.of(total), lines);
        }
        List<TraceLine> capped = new ArrayList<>(lines);
        capped.add(TraceLine.of(capped.size() + 1, NodeIds.CHAPTERVIII, RuleIds.DED_WITHIN_GROSS_TOTAL,
                        StepKind.DEDUCTION, MoneyValue.of(total), MoneyValue.of(limit),
                        version(ctx, RuleIds.DED_WITHIN_GROSS_TOTAL).provenance())
                .withLimitedBy("gross total income " + grossTotal));
        return new NodeResult(MoneyValue.of(limit), capped);
    }

    private static TraceLine nilLine(NodeContext ctx, List<TraceLine> lines, RuleId row, RuleVersion version,
            Money entered) {
        return TraceLine.of(lines.size() + 1, NodeIds.CHAPTERVIII, row, StepKind.DEDUCTION, MoneyValue.of(entered),
                        MoneyValue.of(Money.ZERO), version.provenance())
                .withLimitedBy("not in the " + ctx.regime().code().value() + " regime");
    }

    private static RuleVersion version(NodeContext ctx, RuleId row) {
        return TemporallyApportionedCap.governingVersion(ctx.rule(row), ctx.inputs().temporal());
    }
}
