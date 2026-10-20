package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.EmployerFundContribution;
import com.thiru.wealthlens.taxengine.input.EmployerKind;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.SalaryBases;
import com.thiru.wealthlens.taxengine.rules.cap.AggregateOutcome;
import com.thiru.wealthlens.taxengine.rules.cap.AggregateWithCeiling;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.condition.ItemFacts;
import com.thiru.wealthlens.taxengine.rules.generated.ComponentCodes;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.ItemFactId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * s.124 — the pension scheme, for {@link ChapterEightNode} (S2 s.124; D-132, D-252, D-253): what s.123's sub-cap
 * left with a minor's deposit under one ceiling (s.124(3)–(4)), then the employer's contribution by employer and
 * regime (s.124(1)–(2)).
 */
final class PensionDeduction {

    private static final BaseId SALARY = new BaseId("SALARY");
    /** What an employer pension contribution's row is chosen by, with the regime (D-253). */
    private static final ItemFactId EMPLOYER_KIND = new ItemFactId("employer.kind");

    private PensionDeduction() {
    }

    /** The own-contribution line, then the employer's, added to {@code lines}; none where nothing is entered. */
    static void lines(NodeContext ctx, Money residue, Money minorDeposit, List<EmployerFundContribution> employerPension,
            List<TraceLine> lines) {
        ownPension(ctx, residue, minorDeposit, lines);
        employerPension(ctx, employerPension, lines);
    }

    /** s.124(3)–(4): the pension contribution s.123 did not allow, with a minor's deposit, under one ceiling. */
    private static void ownPension(NodeContext ctx, Money residue, Money minorDeposit, List<TraceLine> lines) {
        Money entered = residue.plus(minorDeposit);
        if (!entered.isGreaterThan(Money.ZERO)) {
            return;
        }
        RuleVersion version = version(ctx, ctx.ruleIdOf(RuleSetIds.RS_DED_NPS_OWN));
        if (!ctx.inRegime(ctx.ruleIdOf(RuleSetIds.RS_DED_NPS_OWN), version).applies()) {
            lines.add(nil(ctx, lines, ctx.ruleIdOf(RuleSetIds.RS_DED_NPS_OWN), version, entered));
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
