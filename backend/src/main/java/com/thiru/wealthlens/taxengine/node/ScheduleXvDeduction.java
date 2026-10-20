package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.EmployerKind;
import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.LifeInsurancePolicy;
import com.thiru.wealthlens.taxengine.pipeline.ComponentSet;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.SalaryBases;
import com.thiru.wealthlens.taxengine.rules.cap.AggregateOutcome;
import com.thiru.wealthlens.taxengine.rules.cap.AggregateWithCeiling;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.component.ComponentKind;
import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.definition.PremiumShareBands;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinitions;
import com.thiru.wealthlens.taxengine.rules.generated.ComponentCodes;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * s.123 — the Schedule XV sums under one ceiling, each life policy's premium first within its share of the sum
 * assured (para 2) and a Government deferred annuity on Government pay (para 1(c)), for {@link ChapterEightNode}
 * (S2 s.123, Schedule XV; D-130, D-134, D-154).
 */
final class ScheduleXvDeduction {

    private static final BaseId SALARY = new BaseId("SALARY");
    private static final BaseId GOVERNMENT_SALARY = new BaseId("GOVERNMENT_SALARY");

    private ScheduleXvDeduction() {
    }

    /**
     * The premium measures, then the s.123 line, added to {@code lines}; returns what the pension sub-cap left, for
     * s.124(3).
     */
    static Money lines(NodeContext ctx, List<LifeInsurancePolicy> policies, Map<ComponentCode, Money> paid,
            List<TraceLine> lines) {
        premiums(ctx, policies, paid, lines);
        return scheduleXv(ctx, paid, lines);
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
            lines.add(nil(ctx, lines, ctx.ruleIdOf(RuleSetIds.RS_DED_SCHEDULE_XV), version, entered));
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
    static void requireGovernmentEmployment(NodeContext ctx, List<ComponentSet.Line> payLines) {
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

    private static TraceLine nil(NodeContext ctx, List<TraceLine> lines, RuleId row, RuleVersion version,
            Money entered) {
        return TraceLine.of(lines.size() + 1, NodeIds.CHAPTERVIII, row, StepKind.DEDUCTION, MoneyValue.of(entered),
                        MoneyValue.of(Money.ZERO), version.provenance())
                .withLimitedBy(ctx.inRegime(row, version).whyNot());
    }

    private static RuleVersion version(NodeContext ctx, RuleId row) {
        return TemporallyApportionedCap.governingVersion(ctx.rule(row), ctx.inputs().temporal());
    }
}
