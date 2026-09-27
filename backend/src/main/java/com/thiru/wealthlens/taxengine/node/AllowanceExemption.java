package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.AllowanceUse;
import com.thiru.wealthlens.taxengine.pipeline.ComponentSet;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapResult;
import com.thiru.wealthlens.taxengine.rules.component.ComponentDefinition;
import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
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
import java.util.Set;

/**
 * The allowance exemptions' lines for {@link ExemptionNode} (Sch. III Table Sl. 12–13 with Rules 2026 r.280;
 * 1961 s.10(14) with Rules 1962 r.2BB; D-101).
 *
 * <p>One line per allowance use entered. A duty allowance (Sl. 12) is exempt up to the lesser of what was paid and
 * what was actually spent; a prescribed one (Sl. 13) up to the lesser of what was paid and its amount per month per
 * child, for at most two children. Which rule a use belongs to is its component's — the registry names the
 * allowance rule it feeds — and a rule that does not apply in the regime leaves no line. The new regime's list
 * (r.2BB(3); r.280(3), read into the new regime) is each rule's regimes, and a version resting on r.280(3)'s wording
 * is marked unverified, so any figure using it shows as provisional.
 */
final class AllowanceExemption {

    /** The allowances whose exemption is a limit per month per child, and so needs children and months. */
    private static final Set<RuleId> PER_CHILD = Set.of(RuleIds.SAL_ALW_CHILDREN_EDUCATION, RuleIds.SAL_ALW_HOSTEL);
    private static final Set<RuleId> ALLOWANCES = Set.of(RuleIds.SAL_ALW_UNIFORM, RuleIds.SAL_ALW_CONVEYANCE,
            RuleIds.SAL_ALW_CHILDREN_EDUCATION, RuleIds.SAL_ALW_HOSTEL);
    private static final BaseId ALLOWANCE_RECEIVED = new BaseId("ALLOWANCE_RECEIVED");
    private static final BaseId AMOUNT_INCURRED = new BaseId("AMOUNT_INCURRED");
    private static final BaseId CHILD = new BaseId("CHILD");
    private static final BaseId MONTH = new BaseId("MONTH");

    private AllowanceExemption() {
    }

    static Set<RuleId> rules() {
        return ALLOWANCES;
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
            if (!version.regimes().appliesIn(ctx.regime().code())) {
                continue;
            }
            Money received = paid(components, use.allowance());
            CapResult exempt = TemporallyApportionedCap.apply(rule, t, context(ruleId, use, received));
            lines.add(TraceLine.of(firstSequence + lines.size(), NodeIds.SALARY_EXEMPTIONS, ruleId,
                            StepKind.EXEMPTION, MoneyValue.of(received), MoneyValue.of(exempt.value()),
                            version.provenance())
                    .withLimitedBy(exempt.limitedBy()));
        }
        return lines;
    }

    /** The allowance rule a component feeds; refused where it feeds none, or more than one. */
    private static RuleId allowanceRule(NodeContext ctx, ComponentCode allowance) {
        ComponentDefinition definition = ctx.pack().components().get(allowance);
        List<RuleId> fed = definition == null ? List.of()
                : definition.feedsInto().stream().filter(ALLOWANCES::contains).toList();
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

    private static CapContext context(RuleId rule, AllowanceUse use, Money received) {
        if (PER_CHILD.contains(rule)) {
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
