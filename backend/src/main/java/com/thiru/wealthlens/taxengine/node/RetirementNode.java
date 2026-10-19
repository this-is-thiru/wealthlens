package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.RetirementEntry;
import com.thiru.wealthlens.taxengine.input.RetirementPayment;
import com.thiru.wealthlens.taxengine.input.StatutoryEntitlement;
import com.thiru.wealthlens.taxengine.pipeline.ComponentSet;
import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapResult;
import com.thiru.wealthlens.taxengine.rules.component.ComponentDefinition;
import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.condition.ItemFacts;
import com.thiru.wealthlens.taxengine.rules.definition.CommutedPensionShare;
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
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The retirement and termination deductions from salary (S2 s.19(1) Table Sl. 3–14 with s.19(2); 1961 s.10(10),
 * (10A), (10AA), (10B), (10C); {@code DR 06} G1.1 step 9; D-103, D-106, D-109).
 *
 * <p>One DEDUCTION line per payment entered, under the entry the taxpayer says it falls under — which turns on the
 * employer and the scheme, facts no amount carries. The amount is the component's, as paid with the salary. Every
 * entry here applies in every regime (s.202(2)(a) excludes Sl. 1 only; s.115BAC(2)(i) none of s.10(10)–(10C)).
 *
 * <p>Other gratuity and other leave encashment (Sl. 6, 14) take the notified limits and the caps across employers
 * and years ({@link NotifiedRetirementLimits}; D-106). Statutory gratuity and retrenchment (Sl. 5, 10) are limited
 * by the amount the labour statute entitles, as the employer computed it, the repealed Acts read as the Codes from
 * 21-11-2025 (D-107, D-109); the line names the separation day, which says which Act the figure answers to. A
 * retirement component paid without an entry is refused rather than taxed in full, which would be a guess. Notice pay feeds no retirement rule, so it is
 * salary taxed in full and needs no entry (s.18(1)(a); {@code RET-NOTICE-001}).
 */
public final class RetirementNode implements ComputationNode {

    /** What a retirement payment's row is chosen by: the entry the taxpayer says it falls under (D-253). */
    static final ItemFactId RETIREMENT_ENTRY = new ItemFactId("retirement.entry");

    private static final BaseId PAYMENT_RECEIVED = new BaseId("PAYMENT_RECEIVED");
    private static final BaseId AMOUNT_UNDER_STATUTE = new BaseId("AMOUNT_UNDER_STATUTE");

    @Override
    public NodeId produces() {
        return NodeIds.SALARY_RETIREMENT;
    }

    @Override
    public Set<NodeId> requires() {
        return Set.of(NodeIds.INPUTS_COMPONENTS);
    }

    @Override
    public Set<RuleSetId> ruleSets() {
        return Set.of(RuleSetIds.RS_RET_ENTRY, RuleSetIds.RS_RET_AGGREGATE);
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        ComponentSet components = ctx.get(NodeIds.INPUTS_COMPONENTS, ComponentSet.class);
        List<RetirementPayment> payments = ctx.inputs().entered().retirementPayments();
        requireEachRetirementComponentEntered(ctx, components, payments);
        NotifiedRetirementLimits.requireOneDay(payments);
        List<TraceLine> trace = new ArrayList<>();
        Map<RetirementEntry, Money> allowedThisYear = new EnumMap<>(RetirementEntry.class);
        for (RetirementPayment payment : payments) {
            payment(ctx, components, payment, allowedThisYear, trace.size() + 1).ifPresent(line -> {
                trace.add(line);
                allowedThisYear.merge(payment.entry(), line.out().asMoney(), Money::plus);
            });
        }
        Money deducted = trace.stream().map(line -> line.out().asMoney()).reduce(Money.ZERO, Money::plus);
        return trace.isEmpty() ? NodeResult.untraced(MoneyValue.of(Money.ZERO))
                : new NodeResult(MoneyValue.of(deducted), trace);
    }

    /** A component as one employment paid it — what a retirement payment is entered against (D-225). */
    private record PaidBy(ComponentCode component, int employment) {
    }

    /**
     * Refuses a payment naming no employment, a component and employment entered twice, and a component an employment
     * paid that feeds a retirement rule without an entry.
     */
    private static void requireEachRetirementComponentEntered(NodeContext ctx, ComponentSet components,
            List<RetirementPayment> payments) {
        int employments = ctx.inputs().entered().employments().size();
        Set<PaidBy> entered = new HashSet<>();
        for (RetirementPayment payment : payments) {
            if (payment.employment() >= employments) {
                throw new IllegalArgumentException(payment.component() + " is entered for employment "
                        + payment.employment() + ", but " + employments + " employment(s) are entered");
            }
            if (!entered.add(new PaidBy(payment.component(), payment.employment()))) {
                throw new IllegalArgumentException(payment.component() + " is entered twice for employment "
                        + payment.employment() + "; enter one retirement payment per component per employment");
            }
        }
        Set<RuleId> retirementRules = ctx.pack().membersOf(Set.of(RuleSetIds.RS_RET_ENTRY));
        for (ComponentSet.Line line : components.in(HeadCode.SALARIES)) {
            ComponentDefinition definition = ctx.pack().components().get(line.code());
            boolean retirement = definition != null
                    && definition.feedsInto().stream().anyMatch(retirementRules::contains);
            if (retirement && !entered.contains(new PaidBy(line.code(), line.employment()))) {
                throw new IllegalArgumentException(line.code() + " is paid but no entry says which of the Act's"
                        + " retirement entries it falls under, which turns on the scheme it was paid under; enter it");
            }
        }
    }

    /** One payment's line; none where its rule does not apply in the regime. */
    private static Optional<TraceLine> payment(NodeContext ctx, ComponentSet components, RetirementPayment payment,
            Map<RetirementEntry, Money> allowedThisYear, int sequence) {
        RuleId ruleId = ctx.ruleIdOf(RuleSetIds.RS_RET_ENTRY, entry(payment));
        ComponentDefinition definition = ctx.pack().components().get(payment.component());
        if (definition == null || !definition.feedsInto().contains(ruleId)) {
            throw new IllegalArgumentException(payment.component() + " cannot be paid under " + payment.entry()
                    + "; it does not feed " + ruleId);
        }
        TemporalContext t = ctx.inputs().temporal();
        RuleRow rule = ctx.rule(ruleId);
        RuleVersion version = TemporallyApportionedCap.governingVersion(rule, t);
        if (!ctx.inRegime(ruleId, version).applies()) {
            return Optional.empty();
        }
        Money received = paid(components, payment);
        Optional<String> barred = voluntaryRetirementBar(ctx, payment);
        if (barred.isPresent()) {
            return Optional.of(ctx.traceLine(sequence, ruleId, StepKind.DEDUCTION, received, Money.ZERO)
                    .withLimitedBy(barred.get()));
        }
        if (payment.entry() == RetirementEntry.OTHER_EMPLOYER_COMMUTATION) {
            // Any employer's gratuity: "the employee has received gratuity" (S2 s.19(1) Table Sl. 8); "receives any
            // gratuity" (1961 s.10(10A)(ii)) — neither names the employer (D-226)
            boolean gratuityReceived = paid(components, ComponentCodes.GRATUITY).isGreaterThan(Money.ZERO);
            Money limit = commutedPensionShare(ruleId, version)
                    .limit(payment.commutedValueOfWholePension().orElseThrow(), gratuityReceived);
            return Optional.of(ctx.traceLine(sequence, ruleId, StepKind.DEDUCTION, received, Money.min(received, limit))
                    .withLimitedBy(received.isLessThanOrEqual(limit) ? "payment received"
                            : gratuityReceived ? "one-third of the pension's commuted value, gratuity also received"
                                    : "one-half of the pension's commuted value"));
        }
        Map<BaseId, Money> paidIn = Map.of(PAYMENT_RECEIVED, received);
        if (NotifiedRetirementLimits.isNotified(ctx, payment)) {
            CapResult deducted = TemporallyApportionedCap.apply(rule, t,
                    NotifiedRetirementLimits.context(ctx, payment, version, paidIn));
            return Optional.of(NotifiedRetirementLimits.capped(ctx, payment,
                    allowedThisYear.getOrDefault(payment.entry(), Money.ZERO),
                    ctx.traceLine(sequence, ruleId, StepKind.DEDUCTION, received, deducted.value())
                            .withLimitedBy(deducted.limitedBy())));
        }
        if (payment.statutory().isPresent()) {
            StatutoryEntitlement entitlement = payment.statutory().get();
            CapResult deducted = TemporallyApportionedCap.apply(rule, t, new CapContext(
                    Map.of(PAYMENT_RECEIVED, received, AMOUNT_UNDER_STATUTE, entitlement.amountUnderStatute())));
            return Optional.of(ctx.traceLine(sequence, ruleId, StepKind.DEDUCTION, received, deducted.value())
                    .withLimitedBy(deducted.limitedBy() + "; separation on " + entitlement.separatedOn()));
        }
        if (payment.entry() == RetirementEntry.VOLUNTARY_RETIREMENT) {
            return Optional.of(voluntaryRetirement(ctx, ruleId, received,
                    allowedThisYear.getOrDefault(payment.entry(), Money.ZERO), sequence));
        }
        CapResult deducted = TemporallyApportionedCap.apply(rule, t, new CapContext(paidIn));
        return Optional.of(ctx.traceLine(sequence, ruleId, StepKind.DEDUCTION, received, deducted.value())
                .withLimitedBy(deducted.limitedBy()));
    }

    /**
     * A voluntary retirement payment, the limit taken once for the employee's year: the rule applied to this year's
     * earlier allowed amounts together with this one, less those (D-227 — "any scheme or schemes", one limit; S2
     * s.19(1) Table Sl. 12; 1961 s.10(10C)). Statutory gratuity, retrenchment and commutation are limited per payment
     * (D-226, D-227); other gratuity and leave encashment are capped on the year's total by notification (D-225).
     */
    private static TraceLine voluntaryRetirement(NodeContext ctx, RuleId ruleId, Money received, Money allowedThisYear,
            int sequence) {
        CapResult together = TemporallyApportionedCap.apply(ctx.rule(ruleId), ctx.inputs().temporal(),
                new CapContext(Map.of(PAYMENT_RECEIVED, allowedThisYear.plus(received))));
        Money allowed = together.value().minus(allowedThisYear);
        String why = allowedThisYear.isGreaterThan(Money.ZERO) && allowed.isLessThan(received)
                ? together.limitedBy() + ", less the " + allowedThisYear + " allowed on this year's earlier voluntary"
                        + " retirement payments, in the order entered (one limit for the employee; D-227)"
                : together.limitedBy();
        return ctx.traceLine(sequence, ruleId, StepKind.DEDUCTION, received, allowed).withLimitedBy(why);
    }

    /**
     * Why a voluntary retirement deduction is barred, if it is: allowed once only, and never after relief for arrears
     * on the amount (s.19(2)(e)(ii)–(iii); 1961 s.10(10C) second and third provisos).
     */
    private static Optional<String> voluntaryRetirementBar(NodeContext ctx, RetirementPayment payment) {
        if (payment.entry() != RetirementEntry.VOLUNTARY_RETIREMENT) {
            return Optional.empty();
        }
        if (payment.arrearsReliefAllowed()) {
            return Optional.of("barred: relief for arrears was allowed on the amount");
        }
        TaxYearId year = ctx.inputs().taxYear();
        return ctx.inputs().ledgers().vrsClaimedIn()
                .filter(claimed -> !claimed.equals(year))
                .map(claimed -> "barred: a voluntary retirement deduction was allowed in " + claimed
                        + ", and it is allowed once only");
    }

    /** The facts a retirement payment's row is chosen by. */
    static ItemFacts entry(RetirementPayment payment) {
        return ItemFacts.of(RETIREMENT_ENTRY, payment.entry().name());
    }

    private static CommutedPensionShare commutedPensionShare(RuleId ruleId, RuleVersion version) {
        return RuleDefinitions.require(version.definition(), CommutedPensionShare.class, ruleId);
    }

    /** What the payment's own employment paid of its component. */
    private static Money paid(ComponentSet components, RetirementPayment payment) {
        return components.in(HeadCode.SALARIES).stream()
                .filter(line -> line.code().equals(payment.component()) && line.employment() == payment.employment())
                .map(ComponentSet.Line::amount)
                .reduce(Money.ZERO, Money::plus);
    }

    private static Money paid(ComponentSet components, ComponentCode component) {
        return components.in(HeadCode.SALARIES).stream()
                .filter(line -> line.code().equals(component))
                .map(ComponentSet.Line::amount)
                .reduce(Money.ZERO, Money::plus);
    }
}
