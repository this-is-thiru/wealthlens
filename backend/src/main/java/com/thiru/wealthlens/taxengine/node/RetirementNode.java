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

    /** Whether relief for arrears was allowed on the payment's amount — what {@code RET-BAR-ARREARS-RELIEF} reads. */
    static final ItemFactId ARREARS_RELIEF_ALLOWED = new ItemFactId("retirement.arrears.relief.allowed");

    /**
     * Whether the ledger holds a deduction under the payment's entry allowed in a tax year other than this one — what
     * {@code RET-BAR-ONCE-ONLY} reads. This year's own is not another year's, so recomputing it is not barred.
     */
    static final ItemFactId ALLOWED_IN_ANOTHER_YEAR = new ItemFactId("retirement.allowed.in.another.year");

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
        return Set.of(RuleSetIds.RS_RET_ENTRY, RuleSetIds.RS_RET_AGGREGATE, RuleSetIds.RS_RET_GRATUITY_AVERAGING,
                RuleSetIds.RS_RET_YEAR_LIMIT, RuleSetIds.RS_RET_BAR,
                RuleSetIds.RS_RET_AGGREGATE_HISTORY);
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
        RuleId ruleId = ctx.ruleIdOf(RuleSetIds.RS_RET_ENTRY, factsOf(ctx, payment));
        requireFeeds(ctx, payment, ruleId);
        TemporalContext t = ctx.inputs().temporal();
        RuleRow rule = ctx.rule(ruleId);
        RuleVersion version = TemporallyApportionedCap.governingVersion(rule, t);
        if (!ctx.inRegime(ruleId, version).applies()) {
            return Optional.empty();
        }
        Money received = paid(components, payment);
        Optional<RuleRow> bar = bar(ctx, payment);
        if (bar.isPresent()) {
            return Optional.of(ctx.traceLine(sequence, ruleId, StepKind.DEDUCTION, received, Money.ZERO)
                    .withLimitedBy(barredBy(ctx, bar.get(), payment)));
        }
        Claim claim = new Claim(payment, received, sequence);
        GoverningRow row = new GoverningRow(ruleId, version);
        if (version.definition().filter(CommutedPensionShare.class::isInstance).isPresent()) {
            return Optional.of(commutation(ctx, components, claim, row));
        }
        return Optional.of(limited(ctx, claim, row, allowedThisYear));
    }

    /** One payment, what was received for it, and its line's place in the node's trace. */
    private record Claim(RetirementPayment payment, Money received, int sequence) {
    }

    private static void requireFeeds(NodeContext ctx, RetirementPayment payment, RuleId ruleId) {
        ComponentDefinition definition = ctx.pack().components().get(payment.component());
        if (definition == null || !definition.feedsInto().contains(ruleId)) {
            throw new IllegalArgumentException(payment.component() + " cannot be paid under " + payment.entry()
                    + "; it does not feed " + ruleId);
        }
    }

    /** A commuted pension from another employer, within its share of the pension's commuted value. */
    private static TraceLine commutation(NodeContext ctx, ComponentSet components, Claim claim, GoverningRow row) {
        Money received = claim.received();
        // Any employer's gratuity: "the employee has received gratuity" (S2 s.19(1) Table Sl. 8); "receives any
        // gratuity" (1961 s.10(10A)(ii)) — neither names the employer (D-226)
        boolean gratuityReceived = paid(components, ComponentCodes.GRATUITY).isGreaterThan(Money.ZERO);
        Money limit = commutedPensionShare(row.id(), row.version())
                .limit(claim.payment().commutedValueOfWholePension().orElseThrow(), gratuityReceived);
        return ctx.traceLine(claim.sequence(), row.id(), StepKind.DEDUCTION, received, Money.min(received, limit))
                .withLimitedBy(received.isLessThanOrEqual(limit) ? "payment received"
                        : gratuityReceived ? "one-third of the pension's commuted value, gratuity also received"
                                : "one-half of the pension's commuted value");
    }

    /** Any other payment, within its row's limit: a notified limit, a statutory entitlement, or the row's own. */
    private static TraceLine limited(NodeContext ctx, Claim claim, GoverningRow row,
            Map<RetirementEntry, Money> allowedThisYear) {
        RetirementPayment payment = claim.payment();
        Money received = claim.received();
        TemporalContext t = ctx.inputs().temporal();
        RuleRow rule = ctx.rule(row.id());
        Map<BaseId, Money> paidIn = Map.of(PAYMENT_RECEIVED, received);
        if (NotifiedRetirementLimits.isNotified(ctx, payment)) {
            CapResult deducted = TemporallyApportionedCap.apply(rule, t,
                    NotifiedRetirementLimits.context(ctx, payment, row.version(), paidIn));
            return NotifiedRetirementLimits.capped(ctx, payment,
                    allowedThisYear.getOrDefault(payment.entry(), Money.ZERO),
                    ctx.traceLine(claim.sequence(), row.id(), StepKind.DEDUCTION, received, deducted.value())
                            .withLimitedBy(deducted.limitedBy()));
        }
        if (payment.statutory().isPresent()) {
            StatutoryEntitlement entitlement = payment.statutory().get();
            CapResult deducted = TemporallyApportionedCap.apply(rule, t, new CapContext(
                    Map.of(PAYMENT_RECEIVED, received, AMOUNT_UNDER_STATUTE, entitlement.amountUnderStatute())));
            return ctx.traceLine(claim.sequence(), row.id(), StepKind.DEDUCTION, received, deducted.value())
                    .withLimitedBy(deducted.limitedBy() + "; separation on " + entitlement.separatedOn());
        }
        if (ctx.optionalRuleOf(RuleSetIds.RS_RET_YEAR_LIMIT, factsOf(ctx, payment)).isPresent()) {
            return voluntaryRetirement(ctx, row.id(), received,
                    allowedThisYear.getOrDefault(payment.entry(), Money.ZERO), claim.sequence());
        }
        CapResult deducted = TemporallyApportionedCap.apply(rule, t, new CapContext(paidIn));
        return ctx.traceLine(claim.sequence(), row.id(), StepKind.DEDUCTION, received, deducted.value())
                .withLimitedBy(deducted.limitedBy());
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
     * The row barring the payment's deduction, if one does: the first {@code RS-RET-BAR} row, in the set's order, that
     * applies in the regime and reaches the payment (s.19(2)(e)(ii)–(iii); 1961 s.10(10C) second and third provisos;
     * W-155b). Which entries a bar reaches, and which bar is named where several do, are the rows' to say.
     */
    private static Optional<RuleRow> bar(NodeContext ctx, RetirementPayment payment) {
        ItemFacts facts = factsOf(ctx, payment);
        TemporalContext t = ctx.inputs().temporal();
        return ctx.rulesOf(RuleSetIds.RS_RET_BAR).stream()
                .filter(row -> ctx.inRegime(row.id(), TemporallyApportionedCap.governingVersion(row, t)).applies())
                .filter(row -> RowReach.holdsFor(ctx, row, facts))
                .findFirst();
    }

    /** The bar's words: the row, and the year the ledger holds for the entry where it holds one, as a fact. */
    private static String barredBy(NodeContext ctx, RuleRow bar, RetirementPayment payment) {
        return "barred by " + bar.id() + Optional.ofNullable(ctx.inputs().ledgers().deductionAllowedIn()
                        .get(payment.entry()))
                .map(year -> "; a deduction under this entry was allowed in " + year)
                .orElse("");
    }

    /** The facts a retirement payment's rows are chosen by: its entry, and what bars a deduction under it. */
    static ItemFacts factsOf(NodeContext ctx, RetirementPayment payment) {
        TaxYearId year = ctx.inputs().taxYear();
        boolean allowedInAnotherYear = Optional.ofNullable(ctx.inputs().ledgers().deductionAllowedIn()
                        .get(payment.entry()))
                .filter(allowed -> !allowed.equals(year))
                .isPresent();
        return new ItemFacts(Map.of(RETIREMENT_ENTRY, payment.entry().name(),
                ARREARS_RELIEF_ALLOWED, Boolean.toString(payment.arrearsReliefAllowed()),
                ALLOWED_IN_ANOTHER_YEAR, Boolean.toString(allowedInAnotherYear)), Map.of(), Map.of());
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
