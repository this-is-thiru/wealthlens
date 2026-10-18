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
import com.thiru.wealthlens.taxengine.rules.definition.CommutedPensionShare;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinitions;
import com.thiru.wealthlens.taxengine.rules.generated.ComponentCodes;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
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
import java.util.TreeSet;

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

    private static final Map<RetirementEntry, RuleId> RULE_FOR_ENTRY = entryRules();

    /**
     * The entries whose payments from a second employer in the year are not yet measured: each reads one employer's
     * facts or a limit whose treatment across employers is unread (D-224, D-225 — W-066g). Other gratuity and other
     * leave encashment are measured per employment with the year's total capped (D-225).
     */
    private static final Set<RetirementEntry> ONE_EMPLOYER_ONLY = Set.of(RetirementEntry.OTHER_EMPLOYER_COMMUTATION,
            RetirementEntry.VOLUNTARY_RETIREMENT, RetirementEntry.STATUTORY_GRATUITY, RetirementEntry.RETRENCHMENT);
    private static final BaseId PAYMENT_RECEIVED = new BaseId("PAYMENT_RECEIVED");
    private static final BaseId AMOUNT_UNDER_STATUTE = new BaseId("AMOUNT_UNDER_STATUTE");

    private static Map<RetirementEntry, RuleId> entryRules() {
        Map<RetirementEntry, RuleId> rules = new EnumMap<>(RetirementEntry.class);
        rules.put(RetirementEntry.GOVERNMENT_GRATUITY, RuleIds.RET_GRATUITY_GOVERNMENT);
        rules.put(RetirementEntry.DEFENCE_RETIRING_GRATUITY, RuleIds.RET_GRATUITY_GOVERNMENT);
        rules.put(RetirementEntry.GOVERNMENT_COMMUTATION, RuleIds.RET_COMMUTED_PENSION_GOVERNMENT);
        rules.put(RetirementEntry.OTHER_EMPLOYER_COMMUTATION, RuleIds.RET_COMMUTED_PENSION_OTHER);
        rules.put(RetirementEntry.FUND_COMMUTATION, RuleIds.RET_COMMUTED_PENSION_FUND);
        rules.put(RetirementEntry.APPROVED_SCHEME_RETRENCHMENT, RuleIds.RET_RETRENCHMENT_APPROVED_SCHEME);
        rules.put(RetirementEntry.VOLUNTARY_RETIREMENT, RuleIds.RET_VOLUNTARY_RETIREMENT);
        rules.put(RetirementEntry.GOVERNMENT_LEAVE_ENCASHMENT, RuleIds.RET_LEAVE_ENCASHMENT_GOVERNMENT);
        rules.put(RetirementEntry.OTHER_GRATUITY, RuleIds.RET_GRATUITY_OTHER);
        rules.put(RetirementEntry.OTHER_LEAVE_ENCASHMENT, RuleIds.RET_LEAVE_ENCASHMENT_OTHER);
        rules.put(RetirementEntry.STATUTORY_GRATUITY, RuleIds.RET_GRATUITY_STATUTORY);
        rules.put(RetirementEntry.RETRENCHMENT, RuleIds.RET_RETRENCHMENT);
        return Map.copyOf(rules);
    }

    @Override
    public NodeId produces() {
        return NodeIds.SALARY_RETIREMENT;
    }

    @Override
    public Set<NodeId> requires() {
        return Set.of(NodeIds.INPUTS_COMPONENTS);
    }

    @Override
    public Set<RuleId> rules() {
        Set<RuleId> rules = new HashSet<>(RULE_FOR_ENTRY.values());
        rules.addAll(NotifiedRetirementLimits.aggregateRules());
        return Set.copyOf(rules);
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        ComponentSet components = ctx.get(NodeIds.INPUTS_COMPONENTS, ComponentSet.class);
        List<RetirementPayment> payments = ctx.inputs().entered().retirementPayments();
        requireEachRetirementComponentEntered(ctx, components, payments);
        requireOneEmploymentWhereNotMeasuredPerEmployment(payments);
        NotifiedRetirementLimits.requireOneDay(payments);
        boolean gratuityReceived = paid(components, ComponentCodes.GRATUITY).isGreaterThan(Money.ZERO);
        List<TraceLine> trace = new ArrayList<>();
        Map<RetirementEntry, Money> allowedThisYear = new EnumMap<>(RetirementEntry.class);
        for (RetirementPayment payment : payments) {
            payment(ctx, components, payment, gratuityReceived, allowedThisYear, trace.size() + 1).ifPresent(line -> {
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
        Set<RuleId> retirementRules = Set.copyOf(RULE_FOR_ENTRY.values());
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

    /** Refuses payments of one entry from more than one employment, where that is not yet measured (W-066g). */
    private static void requireOneEmploymentWhereNotMeasuredPerEmployment(List<RetirementPayment> payments) {
        for (RetirementEntry entry : ONE_EMPLOYER_ONLY) {
            Set<Integer> employments = new TreeSet<>();
            payments.stream().filter(payment -> payment.entry() == entry)
                    .forEach(payment -> employments.add(payment.employment()));
            if (employments.size() > 1) {
                throw new IllegalArgumentException(entry + " is paid by more than one employment " + employments
                        + "; how a second employer's payment is measured is not built — W-066g");
            }
        }
    }

    /** One payment's line; none where its rule does not apply in the regime. */
    private static Optional<TraceLine> payment(NodeContext ctx, ComponentSet components, RetirementPayment payment,
            boolean gratuityReceived, Map<RetirementEntry, Money> allowedThisYear, int sequence) {
        RuleId ruleId = RULE_FOR_ENTRY.get(payment.entry());
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
            return Optional.of(line(sequence, ruleId, received, Money.ZERO, version).withLimitedBy(barred.get()));
        }
        if (payment.entry() == RetirementEntry.OTHER_EMPLOYER_COMMUTATION) {
            Money limit = commutedPensionShare(version)
                    .limit(payment.commutedValueOfWholePension().orElseThrow(), gratuityReceived);
            return Optional.of(line(sequence, ruleId, received, Money.min(received, limit), version)
                    .withLimitedBy(received.isLessThanOrEqual(limit) ? "payment received"
                            : gratuityReceived ? "one-third of the pension's commuted value, gratuity also received"
                                    : "one-half of the pension's commuted value"));
        }
        Map<BaseId, Money> paidIn = Map.of(PAYMENT_RECEIVED, received);
        if (NotifiedRetirementLimits.isNotified(payment.entry())) {
            CapResult deducted = TemporallyApportionedCap.apply(rule, t,
                    NotifiedRetirementLimits.context(ctx, payment, version, paidIn));
            return Optional.of(NotifiedRetirementLimits.capped(ctx, payment.entry(),
                    allowedThisYear.getOrDefault(payment.entry(), Money.ZERO),
                    line(sequence, ruleId, received, deducted.value(), version).withLimitedBy(deducted.limitedBy())));
        }
        if (payment.statutory().isPresent()) {
            StatutoryEntitlement entitlement = payment.statutory().get();
            CapResult deducted = TemporallyApportionedCap.apply(rule, t, new CapContext(
                    Map.of(PAYMENT_RECEIVED, received, AMOUNT_UNDER_STATUTE, entitlement.amountUnderStatute())));
            return Optional.of(line(sequence, ruleId, received, deducted.value(), version).withLimitedBy(
                    deducted.limitedBy() + "; separation on " + entitlement.separatedOn()));
        }
        CapResult deducted = TemporallyApportionedCap.apply(rule, t, new CapContext(paidIn));
        return Optional.of(line(sequence, ruleId, received, deducted.value(), version)
                .withLimitedBy(deducted.limitedBy()));
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

    private static CommutedPensionShare commutedPensionShare(RuleVersion version) {
        RuleId ruleId = RuleIds.RET_COMMUTED_PENSION_OTHER;
        return RuleDefinitions.require(version.definition(), CommutedPensionShare.class, ruleId);
    }

    private static TraceLine line(int sequence, RuleId ruleId, Money received, Money deducted, RuleVersion version) {
        return TraceLine.of(sequence, NodeIds.SALARY_RETIREMENT, ruleId, StepKind.DEDUCTION, MoneyValue.of(received),
                MoneyValue.of(deducted), version.provenance());
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
