package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.EmployerFund;
import com.thiru.wealthlens.taxengine.input.EmployerFundContribution;
import com.thiru.wealthlens.taxengine.input.EmployerKind;
import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.Individual;
import com.thiru.wealthlens.taxengine.input.LifeInsurancePolicy;
import com.thiru.wealthlens.taxengine.input.QualifyingPayment;
import com.thiru.wealthlens.taxengine.pipeline.ComponentSet;
import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import com.thiru.wealthlens.taxengine.pipeline.HeadsAfterSetOff;
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
import com.thiru.wealthlens.taxengine.rules.condition.ItemFacts;
import com.thiru.wealthlens.taxengine.rules.definition.PremiumShareBands;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinitions;
import com.thiru.wealthlens.taxengine.rules.generated.ComponentCodes;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.ItemFactId;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
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
import java.util.stream.Stream;

/**
 * The Chapter VIII deductions built so far: the s.123 family and the pension scheme (S2 ss.122–124, Schedule XV;
 * {@code DR 06} G2.2; D-130, D-132).
 *
 * <p>In order, because each reads the one before: each life policy's premium within its share of the sum assured
 * (Schedule XV para 2), as a measure; the Schedule XV sums under one ceiling, the employee's pension
 * contribution sub-capped first; what that sub-cap left, with a minor's deposit, under s.124(3)–(4) — never the same
 * amount twice (s.124(5), (10)); the employer's pension contribution by employer and regime (s.124(1)–(2)); then all
 * of it within gross total income (s.122(2)). Every base here is gross total income less the capital gains charged at
 * special rates, which no Chapter VIII deduction may reach (s.196(4), s.197(5), s.198(6); D-244). A row the regime excludes, with something entered for it, gives a nil
 * line naming the regime. TY 2025-26 has these rows under the 1961 Act (ss.80C, 80CCC, 80CCD, 80CCE, 80A(2); D-144);
 * a section whose 1961 rows are not built yet is refused naming its item.
 * Then s.126, health insurance and medical expenditure ({@link HealthInsuranceDeduction}; D-136); and s.154, s.127
 * and s.128 ({@link DisabilityDeduction}; D-138); s.129–132, loan interest ({@link LoanInterestDeduction}; D-139); s.125,
 * the Agniveer Corpus Fund ({@link AgniveerDeduction}; D-140); s.153, deposit interest, in both years
 * ({@link DepositInterestDeduction}; D-195); s.133, donations, on adjusted gross total income —
 * gross total income less the rest, held within it ({@link DonationDeduction}; D-142); last, s.134, rent paid, on
 * total income before it — the same, less s.133 too ({@link RentPaidDeduction}; D-141). Each section's base holds the
 * other, so they are in that order, which meets both sections' words unless s.133's qualifying limit binds; with rent
 * claimed as well, that is refused (OQ-23). The other deductions are later items (W-075a3).
 */
public final class ChapterEightNode implements ComputationNode {

    private static final BaseId SALARY = new BaseId("SALARY");
    private static final BaseId GOVERNMENT_SALARY = new BaseId("GOVERNMENT_SALARY");
    private static final BaseId GROSS_TOTAL_INCOME = new BaseId("GROSS_TOTAL_INCOME");
    private static final Set<RuleSetId> PAYMENT_ROWS = Set.of(RuleSetIds.RS_DED_SCHEDULE_XV, RuleSetIds.RS_DED_NPS_OWN);
    private static final Set<RuleSetId> INVESTMENT_RULES = Set.of(RuleSetIds.RS_DED_SCHEDULE_XV,
            RuleSetIds.RS_DED_NPS_OWN, RuleSetIds.RS_DED_NPS_EMPLOYER,
            RuleSetIds.RS_DED_PREMIUM_SHARE);
    /** What an employer pension contribution's row is chosen by, with the regime (D-253). */
    private static final ItemFactId EMPLOYER_KIND = new ItemFactId("employer.kind");

    @Override
    public NodeId produces() {
        return NodeIds.CHAPTERVIII;
    }

    @Override
    public Set<NodeId> requires() {
        return Set.of(NodeIds.GTI, NodeIds.SETOFF, NodeIds.INPUTS_COMPONENTS, NodeIds.SALARY_BASE);
    }

    @Override
    public Set<RuleSetId> ruleSets() {
        Set<RuleSetId> rules = new HashSet<>(INVESTMENT_RULES);
        rules.add(RuleSetIds.RS_DED_WITHIN_GROSS_TOTAL);
        rules.add(RuleSetIds.RS_RATE_CG_SPECIAL);
        rules.addAll(HealthInsuranceDeduction.RULES);
        rules.addAll(DisabilityDeduction.RULES);
        rules.addAll(LoanInterestDeduction.RULES);
        rules.addAll(AgniveerDeduction.RULES);
        rules.addAll(DepositInterestDeduction.RULES);
        rules.addAll(DonationDeduction.RULES);
        rules.addAll(RentPaidDeduction.RULES);
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
        boolean investmentsEntered = !(payLines.isEmpty() && payments.isEmpty() && employerPension.isEmpty()
                && policies.isEmpty());
        if (!investmentsEntered && !sectionFactsEntered(ctx)) {
            return NodeResult.untraced(MoneyValue.of(Money.ZERO));
        }
        requireRowsThisYear(ctx, investmentsEntered);
        requireGovernmentEmployment(ctx, payLines);
        Map<ComponentCode, Money> paid = paid(ctx, payLines, payments);
        List<TraceLine> lines = new ArrayList<>();
        premiums(ctx, policies, paid, lines);
        Money residue = scheduleXv(ctx, paid, lines);
        ownPension(ctx, residue, paid.getOrDefault(ComponentCodes.NPS_MINOR_DEPOSIT, Money.ZERO), lines);
        employerPension(ctx, employerPension, lines);
        lines.addAll(HealthInsuranceDeduction.lines(ctx, lines.size() + 1));
        lines.addAll(DisabilityDeduction.lines(ctx, lines.size() + 1));
        lines.addAll(LoanInterestDeduction.lines(ctx, lines.size() + 1));
        lines.addAll(AgniveerDeduction.lines(ctx, lines.size() + 1));
        lines.addAll(DepositInterestDeduction.lines(ctx, lines.size() + 1));
        DonationDeduction.Outcome donations = DonationDeduction.lines(ctx, totalIncomeBefore(ctx, lines),
                lines.size() + 1);
        if (donations.qualifyingLimitBound() && RentPaidDeduction.entered(ctx)) {
            throw new IllegalArgumentException("s.133's qualifying limit binds and s.134 is claimed: adjusted gross total"
                    + " income is net of s.134 and s.134's base is net of s.133, so neither can be worked first"
                    + " (OQ-23); refused rather than guessed (D-142)");
        }
        lines.addAll(donations.lines());
        RentPaidDeduction.line(ctx, totalIncomeBefore(ctx, lines), lines.size() + 1).ifPresent(lines::add);
        return withinGrossTotal(ctx, lines);
    }

    /** Whether anything s.125–134, s.153 or s.154 reads is entered — the sections after the s.123 family. */
    private static boolean sectionFactsEntered(NodeContext ctx) {
        return !ctx.inputs().entered().healthPayments().isEmpty()
                || !ctx.inputs().entered().disabledDependantSupport().isEmpty()
                || !ctx.inputs().entered().specifiedDiseaseTreatment().isEmpty()
                || !ctx.inputs().entered().educationLoanInterest().isEmpty()
                || !ctx.inputs().entered().purposeLoanInterest().isEmpty()
                || AgniveerDeduction.entered(ctx)
                || DepositInterestDeduction.entered(ctx)
                || DonationDeduction.entered(ctx)
                || RentPaidDeduction.entered(ctx)
                || certifiedDisabled(ctx);
    }

    /** Whether the assessee is certified disabled — s.154 applies with nothing entered. */
    private static boolean certifiedDisabled(NodeContext ctx) {
        return ctx.inputs().assessee() instanceof Individual individual && individual.disability().isPresent();
    }

    /** The pay lines that feed a Chapter VIII row — the employee's own contributions withheld from pay. */
    private static List<ComponentSet.Line> payLines(NodeContext ctx) {
        return ctx.get(NodeIds.INPUTS_COMPONENTS, ComponentSet.class).in(HeadCode.SALARIES).stream()
                .filter(line -> !Collections.disjoint(ctx.pack().components().get(line.code()).feedsInto(),
                        ctx.pack().membersOf(PAYMENT_ROWS)))
                .toList();
    }

    /**
     * Each section with something entered needs its rows in this year's pack; TY 2025-26 gains them section by section
     * (W-075d1–d5), so a section not yet there is refused naming the item that adds it (D-144).
     */
    private static void requireRowsThisYear(NodeContext ctx, boolean investmentsEntered) {
        requireRows(ctx, investmentsEntered, INVESTMENT_RULES, "W-075d1");
        requireRows(ctx, !ctx.inputs().entered().healthPayments().isEmpty(), HealthInsuranceDeduction.RULES, "W-075d2");
        requireRows(ctx, !ctx.inputs().entered().disabledDependantSupport().isEmpty()
                || !ctx.inputs().entered().specifiedDiseaseTreatment().isEmpty() || certifiedDisabled(ctx),
                DisabilityDeduction.RULES, "W-075d3");
        requireRows(ctx, !ctx.inputs().entered().educationLoanInterest().isEmpty()
                || !ctx.inputs().entered().purposeLoanInterest().isEmpty(), LoanInterestDeduction.RULES, "W-075d4");
        requireRows(ctx, AgniveerDeduction.entered(ctx), AgniveerDeduction.RULES, "W-075d5");
        requireRows(ctx, DonationDeduction.entered(ctx), DonationDeduction.RULES, "W-075d5");
        requireRows(ctx, RentPaidDeduction.entered(ctx), RentPaidDeduction.RULES, "W-075d5");
    }

    /** Each set, and the gross-total limit's, holds a row this year: a set with none is a deduction not yet built. */
    private static void requireRows(NodeContext ctx, boolean entered, Set<RuleSetId> sets, String item) {
        boolean held = Stream.concat(sets.stream(), Stream.of(RuleSetIds.RS_DED_WITHIN_GROSS_TOTAL))
                .allMatch(set -> !ctx.pack().ruleSet(set).members().isEmpty());
        if (entered && !held) {
            throw new IllegalArgumentException("What was entered needs Chapter VIII rows the "
                    + ctx.inputs().taxYear().label() + " rule pack does not hold yet (" + item + ", D-144); it cannot"
                    + " be computed for this year");
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
        RuleVersion version = version(ctx, ctx.ruleIdOf(RuleSetIds.RS_DED_PREMIUM_SHARE));
        if (!ctx.inRegime(ctx.ruleIdOf(RuleSetIds.RS_DED_PREMIUM_SHARE), version).applies()) {
            policies.forEach(policy -> paid.merge(ComponentCodes.LIFE_INSURANCE_PREMIUM, policy.premiumPaid(),
                    Money::plus));
            return;
        }
        PremiumShareBands bands = bands(ctx, version);
        for (LifeInsurancePolicy policy : policies) {
            PremiumShareBands.Band band = bands.bandFor(policy.issuedOn(), policy.coversDisabilityOrSpecifiedDisease());
            Money share = band.share().of(policy.actualCapitalSumAssured());
            Money counted = Money.min(policy.premiumPaid(), share);
            lines.add(TraceLine.of(lines.size() + 1, NodeIds.CHAPTERVIII, ctx.ruleIdOf(RuleSetIds.RS_DED_PREMIUM_SHARE), StepKind.MEASURE,
                            MoneyValue.of(policy.premiumPaid()), MoneyValue.of(counted), version.provenance())
                    .withLimitedBy(band.share().asPercentLabel() + " of the sum assured " + policy.actualCapitalSumAssured()
                            + ", a policy issued " + policy.issuedOn()));
            paid.merge(ComponentCodes.LIFE_INSURANCE_PREMIUM, counted, Money::plus);
        }
    }

    private static PremiumShareBands bands(NodeContext ctx, RuleVersion version) {
        RuleId ruleId = ctx.ruleIdOf(RuleSetIds.RS_DED_PREMIUM_SHARE);
        return RuleDefinitions.require(version.definition(), PremiumShareBands.class, ruleId);
    }

    /** s.123: the Schedule XV sums under one ceiling; returns what the pension sub-cap left, for s.124(3). */
    private static Money scheduleXv(NodeContext ctx, Map<ComponentCode, Money> paid, List<TraceLine> lines) {
        RuleVersion version = version(ctx, ctx.ruleIdOf(RuleSetIds.RS_DED_SCHEDULE_XV));
        AggregateWithCeiling cap = (AggregateWithCeiling) version.cap().orElseThrow();
        Map<ComponentCode, Money> contributions = new LinkedHashMap<>();
        cap.contributors().forEach(code -> contributions.put(code, paid.getOrDefault(code, Money.ZERO)));
        Money entered = contributions.values().stream().reduce(Money.ZERO, Money::plus);
        if (!entered.isGreaterThan(Money.ZERO)) {
            return Money.ZERO;
        }
        if (!ctx.inRegime(ctx.ruleIdOf(RuleSetIds.RS_DED_SCHEDULE_XV), version).applies()) {
            lines.add(nilLine(ctx, lines, ctx.ruleIdOf(RuleSetIds.RS_DED_SCHEDULE_XV), version, entered));
            return Money.ZERO;
        }
        SalaryBases bases = ctx.get(NodeIds.SALARY_BASE, SalaryBases.class);
        Money salary = Money.ZERO;
        for (int employment = 0; employment < ctx.inputs().entered().employments().size(); employment++) {
            salary = salary.plus(bases.of(ctx.pack().ruleSet(RuleSetIds.RS_SAL_SALARY_FOR_EMPLOYEE_PENSION), employment));
        }
        AggregateOutcome outcome = cap.applyDetailed(new CapContext(
                Map.of(SALARY, salary, GOVERNMENT_SALARY, governmentSalary(ctx)), contributions, Map.of()));
        lines.add(TraceLine.of(lines.size() + 1, NodeIds.CHAPTERVIII, ctx.ruleIdOf(RuleSetIds.RS_DED_SCHEDULE_XV), StepKind.DEDUCTION,
                        MoneyValue.of(outcome.aggregate()), MoneyValue.of(outcome.allowed()), version.provenance())
                .withLimitedBy(outcome.limitedBy()));
        return outcome.subCapResidue().getOrDefault(ComponentCodes.EMPLOYEE_NPS, Money.ZERO);
    }

    /**
     * The salary Schedule XV para 1(c)'s twenty per cent is measured on: what the Government employments pay in money
     * under "Salaries" — their pay lines, not perquisites (OQ-16; D-154).
     */
    private static Money governmentSalary(NodeContext ctx) {
        List<Employment> employments = ctx.inputs().entered().employments();
        return ctx.get(NodeIds.INPUTS_COMPONENTS, ComponentSet.class).in(HeadCode.SALARIES).stream()
                .filter(line -> employments.get(line.employment()).employerKind() == EmployerKind.GOVERNMENT)
                .filter(line -> ctx.pack().components().get(line.code()).kind() == ComponentKind.SALARY)
                .map(ComponentSet.Line::amount)
                .reduce(Money.ZERO, Money::plus);
    }

    /** Schedule XV para 1(c) is a deduction from salary "payable by or on behalf of the Government" (D-154). */
    private static void requireGovernmentEmployment(NodeContext ctx, List<ComponentSet.Line> payLines) {
        List<Employment> employments = ctx.inputs().entered().employments();
        for (ComponentSet.Line line : payLines) {
            if (line.code().equals(ComponentCodes.GOVERNMENT_DEFERRED_ANNUITY)
                    && employments.get(line.employment()).employerKind() != EmployerKind.GOVERNMENT) {
                throw new IllegalArgumentException("A sum deducted for a deferred annuity under Schedule XV para 1(c)"
                        + " must come from salary payable by the Government; employment " + line.employment()
                        + " is " + employments.get(line.employment()).employerKind() + " (D-154)");
            }
        }
    }

    /** s.124(3)–(4): the pension contribution s.123 did not allow, with a minor's deposit, under one ceiling. */
    private static void ownPension(NodeContext ctx, Money residue, Money minorDeposit, List<TraceLine> lines) {
        Money entered = residue.plus(minorDeposit);
        if (!entered.isGreaterThan(Money.ZERO)) {
            return;
        }
        RuleVersion version = version(ctx, ctx.ruleIdOf(RuleSetIds.RS_DED_NPS_OWN));
        if (!ctx.inRegime(ctx.ruleIdOf(RuleSetIds.RS_DED_NPS_OWN), version).applies()) {
            lines.add(nilLine(ctx, lines, ctx.ruleIdOf(RuleSetIds.RS_DED_NPS_OWN), version, entered));
            return;
        }
        AggregateOutcome outcome = ((AggregateWithCeiling) version.cap().orElseThrow()).applyDetailed(CapContext.ofContributions(
                Map.of(ComponentCodes.EMPLOYEE_NPS, residue, ComponentCodes.NPS_MINOR_DEPOSIT, minorDeposit)));
        lines.add(TraceLine.of(lines.size() + 1, NodeIds.CHAPTERVIII, ctx.ruleIdOf(RuleSetIds.RS_DED_NPS_OWN), StepKind.DEDUCTION,
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
                .of(ctx.pack().ruleSet(RuleSetIds.RS_SAL_SALARY_FOR_EMPLOYER_PENSION), employment);
        Money limit = TemporallyApportionedCap.apply(ctx.rule(row), t, CapContext.of(SALARY, salary)).value();
        Money allowed = Money.min(contributed, limit);
        lines.add(TraceLine.of(lines.size() + 1, NodeIds.CHAPTERVIII, row, StepKind.DEDUCTION,
                        MoneyValue.of(contributed), MoneyValue.of(allowed), version(ctx, row).provenance())
                .withLimitedBy(allowed.isLessThan(contributed)
                        ? "the share of the employer-pension salary " + salary + ", " + limit
                        : "the contribution"));
    }

    /**
     * A Government employer's row in every regime; any other employer's, the row for the regime (s.124(1)(b), (2)) —
     * the rows' conditions on the employment's employer kind and their regimes say which (D-252, D-253).
     */
    private static RuleId employerRow(NodeContext ctx, int employment) {
        EmployerKind kind = ctx.inputs().entered().employments().get(employment).employerKind();
        return ctx.ruleIdOf(RuleSetIds.RS_DED_NPS_EMPLOYER, ItemFacts.of(EMPLOYER_KIND, kind.name()));
    }

    /**
     * Gross total income less the deductions so far, held within it — s.133(7)(a)'s adjusted gross total income
     * before s.133, s.134(5)'s total income before s.134 after it.
     */
    private static Money totalIncomeBefore(NodeContext ctx, List<TraceLine> lines) {
        Money grossTotal = grossTotalForDeductions(ctx);
        return grossTotal.minus(Money.min(deducted(lines), grossTotal));
    }

    private static Money deducted(List<TraceLine> lines) {
        return lines.stream()
                .filter(line -> line.kind() == StepKind.DEDUCTION)
                .map(line -> ((MoneyValue) line.out()).asMoney())
                .reduce(Money.ZERO, Money::plus);
    }

    /** s.122(2): the deductions together never exceed gross total income; a line only where that binds. */
    private static NodeResult withinGrossTotal(NodeContext ctx, List<TraceLine> lines) {
        if (lines.isEmpty()) {
            return NodeResult.untraced(MoneyValue.of(Money.ZERO));
        }
        Money total = deducted(lines);
        Money grossTotal = grossTotalForDeductions(ctx);
        Money limit = TemporallyApportionedCap.apply(ctx.ruleOf(RuleSetIds.RS_DED_WITHIN_GROSS_TOTAL), ctx.inputs().temporal(),
                CapContext.of(GROSS_TOTAL_INCOME, grossTotal)).value();
        if (!total.isGreaterThan(limit)) {
            return new NodeResult(MoneyValue.of(total), lines);
        }
        List<TraceLine> capped = new ArrayList<>(lines);
        capped.add(TraceLine.of(capped.size() + 1, NodeIds.CHAPTERVIII, ctx.ruleIdOf(RuleSetIds.RS_DED_WITHIN_GROSS_TOTAL),
                        StepKind.DEDUCTION, MoneyValue.of(total), MoneyValue.of(limit),
                        version(ctx, ctx.ruleIdOf(RuleSetIds.RS_DED_WITHIN_GROSS_TOTAL)).provenance())
                .withLimitedBy(grossTotalLabel(ctx, grossTotal)));
        return new NodeResult(MoneyValue.of(limit), capped);
    }

    /**
     * Gross total income as Chapter VIII reads it: less the capital gains charged at special rates, "as if the gross
     * total income as so reduced were the gross total income" (S2 s.196(4), s.197(5), s.198(6); 1961 ss.111A(2),
     * 112(2), 112A(5); D-244) — for every base here, the s.122(2) ceiling and s.133's and s.134's alike.
     */
    private static Money grossTotalForDeductions(NodeContext ctx) {
        Money grossTotal = ctx.get(NodeIds.GTI, MoneyValue.class).asMoney().floorAtZero();
        return grossTotal.minus(specialRateGains(ctx)).floorAtZero();
    }

    private static Money specialRateGains(NodeContext ctx) {
        return TaxByBasisNode.atSpecialRates(ctx, ctx.get(NodeIds.SETOFF, HeadsAfterSetOff.class));
    }

    private static String grossTotalLabel(NodeContext ctx, Money grossTotal) {
        Money special = specialRateGains(ctx);
        return special.isGreaterThan(Money.ZERO)
                ? "gross total income less the capital gains at special rates (" + special + ", "
                        + ctx.pack().ruleSet(RuleSetIds.RS_RATE_CG_SPECIAL).members().stream().map(RuleId::value)
                                .sorted().toList() + ") "
                        + grossTotal
                : "gross total income " + grossTotal;
    }

    private static TraceLine nilLine(NodeContext ctx, List<TraceLine> lines, RuleId row, RuleVersion version,
            Money entered) {
        return TraceLine.of(lines.size() + 1, NodeIds.CHAPTERVIII, row, StepKind.DEDUCTION, MoneyValue.of(entered),
                        MoneyValue.of(Money.ZERO), version.provenance())
                .withLimitedBy(ctx.inRegime(row, version).whyNot());
    }

    private static RuleVersion version(NodeContext ctx, RuleId row) {
        return TemporallyApportionedCap.governingVersion(ctx.rule(row), ctx.inputs().temporal());
    }
}
