package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.Donation;
import com.thiru.wealthlens.taxengine.input.Individual;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.condition.ItemFacts;
import com.thiru.wealthlens.taxengine.rules.definition.CashPaymentLimit;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinitions;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.ItemFactId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * s.133 — donations — for {@link ChapterEightNode} (S2; D-142). Each donation the cash bar (5) or the donee-report bar
 * (6) stops gets a nil line; the rest count by the share row that reaches their clause (whole or half), without the
 * qualifying limit or within it as that row reaches them (W-156a, D-344). The limited sums together count up to 10% of
 * adjusted gross total income (s.133(2)), before the share is taken. Where that limit binds the outcome says so,
 * because s.134's base and adjusted gross total income then each depend on the other (OQ-23).
 */
final class DonationDeduction {

    static final Set<RuleSetId> RULES = Set.of(RuleSetIds.RS_DED_DONATION_WHOLE, RuleSetIds.RS_DED_DONATION_HALF,
            RuleSetIds.RS_DED_DONATION_QUALIFYING, RuleSetIds.RS_DED_DONATION_CASH,
            RuleSetIds.RS_DED_DONATION_REPORT);

    /** The donation's clause — the {@code DonationClause} constant name — which the share and limit rows read. */
    static final ItemFactId CLAUSE = new ItemFactId("donation.clause");

    /** Whether the donee reported the donation — what {@code DED-DONATION-UNREPORTED} reads. */
    static final ItemFactId REPORTED_BY_DONEE = new ItemFactId("donation.reported.by.donee");

    /** The sets whose row deducts a donation's share, in the order their lines are written. */
    private static final List<RuleSetId> SHARES = List.of(RuleSetIds.RS_DED_DONATION_WHOLE,
            RuleSetIds.RS_DED_DONATION_HALF);

    private static final BaseId AMOUNT_PAID = new BaseId("AMOUNT_PAID");
    private static final BaseId ADJUSTED_GTI = new BaseId("ADJUSTED_GTI");

    /**
     * The lines, and whether the qualifying limit bound.
     *
     * @param lines the s.133 lines, numbered on from the lines before them
     * @param qualifyingLimitBound whether the limited sums exceeded 10% of adjusted gross total income
     */
    record Outcome(List<TraceLine> lines, boolean qualifyingLimitBound) {

        Outcome {
            lines = List.copyOf(lines);
        }
    }

    /** What stops a donation: the row its nil line cites, and why. */
    private record Bar(RuleId row, String reason) {
    }

    /** The donations' sums that count, by share row: without the qualifying limit, and within it. */
    private record Counted(Map<RuleId, Money> unlimited, Map<RuleId, Money> limited) {

        Money limitedTotal() {
            return limited.values().stream().reduce(Money.ZERO, Money::plus);
        }
    }

    private DonationDeduction() {
    }

    /** Whether any donation is entered. */
    static boolean entered(NodeContext ctx) {
        return !ctx.inputs().entered().donations().isEmpty();
    }

    /** The lines, numbered from {@code firstSequence}, on {@code adjustedGrossTotal}; none where nothing is entered. */
    static Outcome lines(NodeContext ctx, Money adjustedGrossTotal, int firstSequence) {
        List<Donation> donations = ctx.inputs().entered().donations();
        if (donations.isEmpty()) {
            return new Outcome(List.of(), false);
        }
        if (!(ctx.inputs().assessee() instanceof Individual)) {
            throw new IllegalArgumentException("s.133 is built for an individual; a Hindu undivided family's donations"
                    + " are not (D-142)");
        }
        List<TraceLine> lines = new ArrayList<>();
        if (!ctx.inRegime(ctx.ruleIdOf(RuleSetIds.RS_DED_DONATION_WHOLE), version(ctx, ctx.ruleIdOf(RuleSetIds.RS_DED_DONATION_WHOLE))).applies()) {
            notInRegime(ctx, donations, firstSequence, lines);
            return new Outcome(lines, false);
        }
        Counted counted = counted(ctx, donations, firstSequence, lines);
        boolean bound = false;
        Money limited = counted.limitedTotal();
        if (limited.isGreaterThan(Money.ZERO)) {
            Money limit = apply(ctx, ctx.ruleIdOf(RuleSetIds.RS_DED_DONATION_QUALIFYING), ADJUSTED_GTI, adjustedGrossTotal);
            if (limited.isGreaterThan(limit)) {
                bound = true;
                counted = withinLimit(counted, limit);
                lines.add(TraceLine.of(firstSequence + lines.size(), NodeIds.CHAPTERVIII,
                                ctx.ruleIdOf(RuleSetIds.RS_DED_DONATION_QUALIFYING), StepKind.MEASURE, MoneyValue.of(limited),
                                MoneyValue.of(limit), version(ctx, ctx.ruleIdOf(RuleSetIds.RS_DED_DONATION_QUALIFYING)).provenance())
                        .withLimitedBy("10% of adjusted gross total income " + adjustedGrossTotal));
            }
        }
        for (RuleSetId share : SHARES) {
            RuleId row = ctx.ruleIdOf(share);
            deduct(ctx, row, counted.unlimited().getOrDefault(row, Money.ZERO), "without limit", firstSequence, lines);
            deduct(ctx, row, counted.limited().getOrDefault(row, Money.ZERO), "within the qualifying limit",
                    firstSequence, lines);
        }
        return new Outcome(lines, bound);
    }

    /** Sums each donation the bars let through by its share row; a nil line for each one they stop. */
    private static Counted counted(NodeContext ctx, List<Donation> donations, int firstSequence,
            List<TraceLine> lines) {
        CashPaymentLimit cash = RuleDefinitions.require(version(ctx, ctx.ruleIdOf(RuleSetIds.RS_DED_DONATION_CASH)).definition(),
                CashPaymentLimit.class, ctx.ruleIdOf(RuleSetIds.RS_DED_DONATION_CASH));
        RuleRow qualifying = ctx.ruleOf(RuleSetIds.RS_DED_DONATION_QUALIFYING);
        Map<RuleId, Money> unlimited = new LinkedHashMap<>();
        Map<RuleId, Money> limited = new LinkedHashMap<>();
        for (Donation donation : donations) {
            RuleId share = shareRow(ctx, donation);
            Optional<Bar> bar = bar(ctx, donation, share, cash);
            if (bar.isPresent()) {
                lines.add(ctx.traceLine(firstSequence + lines.size(), bar.get().row(), StepKind.DEDUCTION,
                                donation.amount(), Money.ZERO)
                        .withLimitedBy(bar.get().reason()));
                continue;
            }
            (RowReach.holdsFor(ctx, qualifying, factsOf(donation)) ? limited : unlimited)
                    .merge(share, donation.amount(), Money::plus);
        }
        return new Counted(unlimited, limited);
    }

    /** The row of the share sets that reaches the donation's clause; exactly one must. */
    private static RuleId shareRow(NodeContext ctx, Donation donation) {
        List<RuleId> reaching = SHARES.stream().map(ctx::ruleIdOf)
                .filter(row -> RowReach.holdsFor(ctx, ctx.rule(row), factsOf(donation)))
                .toList();
        if (reaching.size() != 1) {
            throw new IllegalStateException("A " + donation.clause() + " donation is reached by " + reaching + " of the"
                    + " share rows; exactly one must deduct it");
        }
        return reaching.getFirst();
    }

    /** s.133(5), then s.133(6): what stops a donation, if anything. */
    private static Optional<Bar> bar(NodeContext ctx, Donation donation, RuleId share, CashPaymentLimit cash) {
        if (!cash.allows(donation.amount(), donation.paidInCash())) {
            return Optional.of(new Bar(ctx.ruleIdOf(RuleSetIds.RS_DED_DONATION_CASH), "paid in cash over " + cash.atMost() + " (s.133(5))"));
        }
        RuleRow report = ctx.ruleOf(RuleSetIds.RS_DED_DONATION_REPORT);
        if (RowReach.holdsFor(ctx, report, factsOf(donation))) {
            return Optional.of(new Bar(share, "barred by " + report.id()));
        }
        return Optional.empty();
    }

    /** s.133(2): the limited sums cut to the limit — refused where they fall in more than one share (OQ-24). */
    private static Counted withinLimit(Counted counted, Money limit) {
        List<RuleId> shares = counted.limited().entrySet().stream()
                .filter(entry -> entry.getValue().isGreaterThan(Money.ZERO)).map(Map.Entry::getKey).toList();
        if (shares.size() > 1) {
            throw new IllegalArgumentException("Limited donations deducted at different shares (" + shares + ") together"
                    + " exceed the qualifying limit " + limit + "; s.133(2) does not say which sums the excess is taken"
                    + " from (OQ-24), so it is refused rather than guessed (D-142)");
        }
        return new Counted(counted.unlimited(), Map.of(shares.getFirst(), limit));
    }

    private static void notInRegime(NodeContext ctx, List<Donation> donations, int firstSequence,
            List<TraceLine> lines) {
        Map<RuleId, Money> entered = new LinkedHashMap<>();
        SHARES.forEach(share -> entered.put(ctx.ruleIdOf(share), Money.ZERO));
        donations.forEach(donation -> entered.merge(shareRow(ctx, donation), donation.amount(), Money::plus));
        entered.forEach((row, amount) -> {
            if (amount.isGreaterThan(Money.ZERO)) {
                lines.add(ctx.traceLine(firstSequence + lines.size(), row, StepKind.DEDUCTION, amount, Money.ZERO)
                        .withLimitedBy(ctx.inRegime(row, version(ctx, row)).whyNot()));
            }
        });
    }

    private static void deduct(NodeContext ctx, RuleId row, Money counted, String what, int firstSequence,
            List<TraceLine> lines) {
        if (counted.isGreaterThan(Money.ZERO)) {
            lines.add(ctx.traceLine(firstSequence + lines.size(), row, StepKind.DEDUCTION,
                            counted, apply(ctx, row, AMOUNT_PAID, counted))
                    .withLimitedBy(what));
        }
    }

    /** The facts a donation's rows read. */
    private static ItemFacts factsOf(Donation donation) {
        return new ItemFacts(Map.of(CLAUSE, donation.clause().name(),
                REPORTED_BY_DONEE, Boolean.toString(donation.reportedByDonee())), Map.of(), Map.of());
    }

    private static Money apply(NodeContext ctx, RuleId row, BaseId base, Money amount) {
        return TemporallyApportionedCap.apply(ctx.rule(row), ctx.inputs().temporal(), CapContext.of(base, amount))
                .value();
    }

    private static RuleVersion version(NodeContext ctx, RuleId row) {
        return TemporallyApportionedCap.governingVersion(ctx.rule(row), ctx.inputs().temporal());
    }
}
