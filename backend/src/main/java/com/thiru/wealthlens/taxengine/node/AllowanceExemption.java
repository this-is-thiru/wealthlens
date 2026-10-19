package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.AllowanceUse;
import com.thiru.wealthlens.taxengine.input.DisabledTransportClaim;
import com.thiru.wealthlens.taxengine.pipeline.ComponentSet;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.RegimeVerdict;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapResult;
import com.thiru.wealthlens.taxengine.rules.component.ComponentDefinition;
import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.definition.DisabledTransportLimit;
import com.thiru.wealthlens.taxengine.rules.definition.HeadTotal;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinitions;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The allowance exemptions' lines for {@link ExemptionNode} (Sch. III Table Sl. 12–13 with Rules 2026 r.280;
 * 1961 s.10(14) with Rules 1962 r.2BB; D-101).
 *
 * <p>One line per allowance use entered. A duty allowance (Sl. 12) is exempt up to the lesser of what was paid and
 * what was actually spent; a prescribed one (Sl. 13) up to the lesser of what was paid and its amount per month per
 * child, for at most two children. Which rule a use belongs to is its component's — the registry names the
 * allowance rule it feeds — and a rule the regime forbids gives a nil line for what was received, naming the
 * provision (D-166). The new regime's list (r.2BB(3); r.280(3), read into the new regime) is the regime's
 * disallowance set — what is not prescribed there is forbidden by s.202(2)(a)(ii) — and a version resting on
 * r.280(3)'s wording is marked unverified, so any figure using it shows as provisional.
 *
 * <p>A disabled employee's transport allowance (Sl. 13 with r.280(2) Sl. 10; r.2BB(2) Sl. 11; D-102) is claimed
 * apart from the uses, since its limit turns on the disability and, from TY 2026-27, on the city class and the
 * dearness allowance thereon: exempt up to the lesser of what was paid and the monthly limit for each month covered.
 * A disability the year does not list gets a nil line naming it.
 */
final class AllowanceExemption {

    /** The allowances whose exemption is a limit per month per child, and so needs children and months. */
    private static final Set<RuleSetId> PER_CHILD = Set.of(RuleSetIds.RS_SAL_ALW_CHILDREN_EDUCATION, RuleSetIds.RS_SAL_ALW_HOSTEL);
    private static final Set<RuleSetId> ALLOWANCES = Set.of(RuleSetIds.RS_SAL_ALW_UNIFORM, RuleSetIds.RS_SAL_ALW_CONVEYANCE,
            RuleSetIds.RS_SAL_ALW_CHILDREN_EDUCATION, RuleSetIds.RS_SAL_ALW_HOSTEL);
    private static final BaseId ALLOWANCE_RECEIVED = new BaseId("ALLOWANCE_RECEIVED");
    private static final BaseId AMOUNT_INCURRED = new BaseId("AMOUNT_INCURRED");
    private static final BaseId CHILD = new BaseId("CHILD");
    private static final BaseId MONTH = new BaseId("MONTH");

    private AllowanceExemption() {
    }

    static Set<RuleSetId> rules() {
        Set<RuleSetId> rules = new HashSet<>(ALLOWANCES);
        rules.add(RuleSetIds.RS_SAL_ALW_TRANSPORT_DISABLED);
        return Set.copyOf(rules);
    }

    /** One EXEMPTION line per allowance use, numbered from {@code firstSequence}. */
    static List<TraceLine> lines(NodeContext ctx, int firstSequence) {
        TemporalContext t = ctx.inputs().temporal();
        ComponentSet components = ctx.get(NodeIds.INPUTS_COMPONENTS, ComponentSet.class);
        Set<ComponentCode> seen = new HashSet<>();
        List<TraceLine> lines = new ArrayList<>();
        for (AllowanceUse use : ctx.inputs().entered().allowanceUses()) {
            if (!seen.add(use.allowance())) {
                throw new IllegalArgumentException(use.allowance() + " is entered twice; enter one use per allowance");
            }
            RuleId ruleId = allowanceRule(ctx, use.allowance());
            RuleRow rule = ctx.rule(ruleId);
            RuleVersion version = TemporallyApportionedCap.governingVersion(rule, t);
            Money received = paid(components, use.allowance());
            RegimeVerdict verdict = ctx.inRegime(ruleId, version);
            if (!verdict.applies()) {
                lines.add(TraceLine.of(firstSequence + lines.size(), NodeIds.SALARY_EXEMPTIONS, ruleId,
                                StepKind.EXEMPTION, MoneyValue.of(received), MoneyValue.of(Money.ZERO),
                                version.provenance())
                        .withLimitedBy(verdict.whyNot()));
                continue;
            }
            CapResult exempt = TemporallyApportionedCap.apply(rule, t, context(ctx, ruleId, use, received));
            lines.add(TraceLine.of(firstSequence + lines.size(), NodeIds.SALARY_EXEMPTIONS, ruleId,
                            StepKind.EXEMPTION, MoneyValue.of(received), MoneyValue.of(exempt.value()),
                            version.provenance())
                    .withLimitedBy(exempt.limitedBy()));
        }
        ctx.inputs().entered().disabledTransport()
                .flatMap(claim -> transport(ctx, components, claim, firstSequence + lines.size()))
                .ifPresent(lines::add);
        return lines;
    }

    /** The transport line: the least of what was paid and the limit for the months covered; nil where not listed. */
    private static Optional<TraceLine> transport(NodeContext ctx, ComponentSet components,
            DisabledTransportClaim claim, int sequence) {
        RuleId ruleId = ctx.ruleIdOf(RuleSetIds.RS_SAL_ALW_TRANSPORT_DISABLED);
        RuleVersion version = TemporallyApportionedCap.governingVersion(ctx.rule(ruleId), ctx.inputs().temporal());
        if (!ctx.inRegime(ruleId, version).applies()) {
            return Optional.empty();
        }
        DisabledTransportLimit limit = transportLimit(ctx, version);
        Money received = HeadTotalLines.of(ctx, components, new HeadTotal(HeadCode.SALARIES), ruleId).total();
        if (!limit.admits(claim.condition())) {
            return Optional.of(TraceLine.of(sequence, NodeIds.SALARY_EXEMPTIONS, ruleId, StepKind.EXEMPTION,
                            MoneyValue.of(received), MoneyValue.of(Money.ZERO), version.provenance())
                    .withLimitedBy("not listed: " + claim.condition() + " is not among this year's disabilities "
                            + limit.conditions().stream().map(Enum::name).sorted().toList()));
        }
        Money ceiling = limit.monthlyLimit(claim.city(), claim.dearnessAllowanceThereon())
                .times(claim.monthsCovered());
        return Optional.of(TraceLine.of(sequence, NodeIds.SALARY_EXEMPTIONS, ruleId, StepKind.EXEMPTION,
                        MoneyValue.of(received), MoneyValue.of(Money.min(received, ceiling)), version.provenance())
                .withLimitedBy(received.isLessThanOrEqual(ceiling) ? "allowance received" : "limit per month"));
    }

    private static DisabledTransportLimit transportLimit(NodeContext ctx, RuleVersion version) {
        RuleId ruleId = ctx.ruleIdOf(RuleSetIds.RS_SAL_ALW_TRANSPORT_DISABLED);
        return RuleDefinitions.require(version.definition(), DisabledTransportLimit.class, ruleId);
    }

    /** The allowance rule a component feeds; refused where it feeds none, or more than one. */
    private static RuleId allowanceRule(NodeContext ctx, ComponentCode allowance) {
        ComponentDefinition definition = ctx.pack().components().get(allowance);
        Set<RuleId> allowanceRows = ALLOWANCES.stream()
                .flatMap(set -> ctx.pack().ruleSet(set).members().stream())
                .collect(Collectors.toSet());
        List<RuleId> fed = definition == null ? List.of()
                : definition.feedsInto().stream().filter(allowanceRows::contains).toList();
        if (definition != null && definition.feedsInto().contains(ctx.ruleIdOf(RuleSetIds.RS_SAL_ALW_TRANSPORT_DISABLED))) {
            throw new IllegalArgumentException(allowance + " is exempt by the disability and the months covered;"
                    + " enter it as a disabled transport claim, not an allowance use");
        }
        if (fed.size() != 1) {
            throw new IllegalArgumentException(allowance + " has no allowance exemption to claim a use against");
        }
        return fed.getFirst();
    }

    private static Money paid(ComponentSet components, ComponentCode allowance) {
        return components.in(HeadCode.SALARIES).stream()
                .filter(line -> line.code().equals(allowance))
                .map(ComponentSet.Line::amount)
                .reduce(Money.ZERO, Money::plus);
    }

    private static CapContext context(NodeContext ctx, RuleId rule, AllowanceUse use, Money received) {
        if (ctx.pack().membersOf(PER_CHILD).contains(rule)) {
            if (use.childrenCovered().isEmpty()) {
                throw new IllegalArgumentException(use.allowance() + " is exempt per month per child; enter the"
                        + " children and months it covered");
            }
            return new CapContext(Map.of(ALLOWANCE_RECEIVED, received), Map.of(),
                    Map.of(CHILD, use.childrenCovered().get(), MONTH, use.monthsCovered().orElseThrow()));
        }
        Money incurred = use.amountIncurred().orElseThrow(() -> new IllegalArgumentException(use.allowance()
                + " is exempt to the extent actually incurred; enter the amount spent"));
        return new CapContext(Map.of(ALLOWANCE_RECEIVED, received, AMOUNT_INCURRED, incurred));
    }
}
