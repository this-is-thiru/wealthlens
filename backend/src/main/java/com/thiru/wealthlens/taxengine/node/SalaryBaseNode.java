package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.pipeline.ComponentSet;
import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.SalaryBases;
import com.thiru.wealthlens.taxengine.rules.component.SalaryBaseInclusion;
import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.definition.HeadTotal;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinition;
import com.thiru.wealthlens.taxengine.rules.definition.SalaryBase;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The salary bases: the narrow "salary" that HRA, gratuity, leave encashment and both pension limits are
 * measured against — basic, and dearness allowance where the terms of employment provide, but no other
 * allowance or perquisite (S4 r.279(2)(b); S2 s.19(2)(b), s.124(13)(b), Sch. XV para 1(y); D-092).
 *
 * <p>One function for four rules, because the Acts use one definition four times (rulebook H-4). The node
 * names no component: which lines count, and which only where the terms provide, is each component's
 * {@code salaryBaseFor} in the registry; whether the terms provide is the employment's. Each base is kept per
 * employment, since HRA and gratuity are measured against one employer's salary.
 */
public final class SalaryBaseNode implements ComputationNode {

    /** In the order they are traced. */
    private static final List<RuleId> BASES = List.of(
            RuleIds.SAL_SALARY_FOR_HRA,
            RuleIds.SAL_SALARY_FOR_GRATUITY,
            RuleIds.SAL_SALARY_FOR_EMPLOYER_PENSION,
            RuleIds.SAL_SALARY_FOR_EMPLOYEE_PENSION);

    @Override
    public NodeId produces() {
        return NodeIds.SALARY_BASE;
    }

    @Override
    public Set<NodeId> requires() {
        return Set.of(NodeIds.INPUTS_COMPONENTS);
    }

    @Override
    public Set<RuleId> rules() {
        return new LinkedHashSet<>(BASES);
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        ComponentSet components = ctx.get(NodeIds.INPUTS_COMPONENTS, ComponentSet.class);
        List<Employment> employments = ctx.inputs().entered().employments();
        Map<RuleId, List<Money>> byRule = new LinkedHashMap<>();
        List<TraceLine> trace = new ArrayList<>();
        for (RuleId rule : BASES) {
            RuleVersion version = definingVersion(ctx, rule);
            Map<HeadCode, List<ComponentSet.Line>> counted = new EnumMap<>(HeadCode.class);
            List<Money> perEmployment = new ArrayList<>(Collections.nCopies(employments.size(), Money.ZERO));
            components.byHead().forEach((head, lines) -> lines.forEach(line -> {
                if (counts(ctx, line, rule, employments.get(line.employment()))) {
                    counted.computeIfAbsent(head, key -> new ArrayList<>()).add(line);
                    perEmployment.set(line.employment(), perEmployment.get(line.employment()).plus(line.amount()));
                }
            }));
            byRule.put(rule, perEmployment);
            trace.add(TraceLine.of(trace.size() + 1, produces(), rule, StepKind.MEASURE, new ComponentSet(counted),
                    new SalaryBases(Map.of(rule, perEmployment)), version.provenance()));
        }
        return new NodeResult(new SalaryBases(byRule), trace);
    }

    /** Whether a line is in this base: the registry lists it, and where it counts only on the terms, they provide. */
    private static boolean counts(NodeContext ctx, ComponentSet.Line line, RuleId rule, Employment employment) {
        Optional<SalaryBaseInclusion> inclusion = ctx.pack().components().get(line.code()).salaryBaseFor().stream()
                .filter(entry -> entry.rule().equals(rule))
                .findFirst();
        return inclusion.isPresent()
                && (!inclusion.get().onlyWhereTermsProvide() || employment.termsIncludeDearnessAllowance());
    }

    /** The year's version of a base rule, refused unless it states a salary base in this regime. */
    private static RuleVersion definingVersion(NodeContext ctx, RuleId rule) {
        RuleVersion version = ctx.rule(rule).versionAt(ctx.inputs().temporal());
        if (!version.regimes().appliesIn(ctx.regime().code())) {
            throw new IllegalStateException(rule + " does not apply in regime " + ctx.regime().code()
                    + "; a salary base is defined in every regime, so the row is wrong");
        }
        RuleDefinition definition = version.definition().orElseThrow(() -> new IllegalStateException(
                rule + " carries no definition; there is no salary base to measure"));
        return switch (definition) {
            case SalaryBase salaryBase -> version;
            case HeadTotal headTotal -> throw new IllegalStateException(
                    rule + " defines a head's total, not a salary base");
        };
    }
}
