package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.Donation;
import com.thiru.wealthlens.taxengine.input.DonationClause;
import com.thiru.wealthlens.taxengine.input.Individual;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.definition.CashPaymentLimit;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinitions;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * s.133 — donations — for {@link ChapterEightNode} (S2; D-142). Each donation the cash bar (5) or, for a charitable
 * institution, the missing report (6) stops gets a nil line; the rest count in four groups — whole or half, without
 * the qualifying limit or within it. The limited sums together count up to 10% of adjusted gross total income
 * (s.133(2)), before the whole or the half is taken. Where that limit binds the outcome says so, because s.134's base
 * and adjusted gross total income then each depend on the other (OQ-23).
 */
final class DonationDeduction {

    static final Set<RuleId> RULES = Set.of(RuleIds.DED_DONATION_WHOLE, RuleIds.DED_DONATION_HALF,
            RuleIds.DED_DONATION_QUALIFYING, RuleIds.DED_DONATION_CASH);

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

    /** What stops a donation: the row whose provision does, and why. */
    private record Bar(RuleId row, String reason) {
    }

    /** The donations' sums that count, by group. */
    private record Counted(Money whole, Money wholeLimited, Money half, Money halfLimited) {

        Money limited() {
            return wholeLimited.plus(halfLimited);
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
        if (!version(ctx, RuleIds.DED_DONATION_WHOLE).regimes().appliesIn(ctx.regime().code())) {
            notInRegime(ctx, donations, firstSequence, lines);
            return new Outcome(lines, false);
        }
        Counted counted = counted(ctx, donations, firstSequence, lines);
        boolean bound = false;
        Money limited = counted.limited();
        if (limited.isGreaterThan(Money.ZERO)) {
            Money limit = apply(ctx, RuleIds.DED_DONATION_QUALIFYING, ADJUSTED_GTI, adjustedGrossTotal);
            if (limited.isGreaterThan(limit)) {
                bound = true;
                counted = withinLimit(counted, limit);
                lines.add(TraceLine.of(firstSequence + lines.size(), NodeIds.CHAPTERVIII,
                                RuleIds.DED_DONATION_QUALIFYING, StepKind.MEASURE, MoneyValue.of(limited),
                                MoneyValue.of(limit), version(ctx, RuleIds.DED_DONATION_QUALIFYING).provenance())
                        .withLimitedBy("10% of adjusted gross total income " + adjustedGrossTotal));
            }
        }
        deduct(ctx, RuleIds.DED_DONATION_WHOLE, counted.whole(), "the whole, without limit", firstSequence, lines);
        deduct(ctx, RuleIds.DED_DONATION_WHOLE, counted.wholeLimited(), "the whole, within the qualifying limit",
                firstSequence, lines);
        deduct(ctx, RuleIds.DED_DONATION_HALF, counted.half(), "half, without limit", firstSequence, lines);
        deduct(ctx, RuleIds.DED_DONATION_HALF, counted.halfLimited(), "half, within the qualifying limit",
                firstSequence, lines);
        return new Outcome(lines, bound);
    }

    /** Sums each donation the bars let through into its group; a nil line for each one they stop. */
    private static Counted counted(NodeContext ctx, List<Donation> donations, int firstSequence,
            List<TraceLine> lines) {
        CashPaymentLimit cash = RuleDefinitions.require(version(ctx, RuleIds.DED_DONATION_CASH).definition(),
                CashPaymentLimit.class, RuleIds.DED_DONATION_CASH);
        Money whole = Money.ZERO;
        Money wholeLimited = Money.ZERO;
        Money half = Money.ZERO;
        Money halfLimited = Money.ZERO;
        for (Donation donation : donations) {
            Optional<Bar> bar = bar(donation, cash);
            if (bar.isPresent()) {
                lines.add(line(ctx, bar.get().row(), donation.amount(), Money.ZERO, bar.get().reason(),
                        firstSequence + lines.size()));
                continue;
            }
            Money amount = donation.amount();
            switch (donation.clause()) {
                case NAMED_FUND -> whole = whole.plus(amount);
                case FAMILY_PLANNING -> wholeLimited = wholeLimited.plus(amount);
                case DROUGHT_RELIEF_FUND -> half = half.plus(amount);
                case CHARITABLE_INSTITUTION, PUBLIC_BODY -> halfLimited = halfLimited.plus(amount);
            }
        }
        return new Counted(whole, wholeLimited, half, halfLimited);
    }

    /** s.133(5), then s.133(6): what stops a donation, if anything. */
    private static Optional<Bar> bar(Donation donation, CashPaymentLimit cash) {
        if (!cash.allows(donation.amount(), donation.paidInCash())) {
            return Optional.of(new Bar(RuleIds.DED_DONATION_CASH, "paid in cash over " + cash.atMost() + " (s.133(5))"));
        }
        if (donation.clause() == DonationClause.CHARITABLE_INSTITUTION && !donation.reportedByDonee()) {
            return Optional.of(new Bar(RuleIds.DED_DONATION_HALF, "not reported by the donee institution (s.133(6))"));
        }
        return Optional.empty();
    }

    /** s.133(2): the limited sums cut to the limit — refused where whole and half sums mix (OQ-24). */
    private static Counted withinLimit(Counted counted, Money limit) {
        if (counted.wholeLimited().isGreaterThan(Money.ZERO) && counted.halfLimited().isGreaterThan(Money.ZERO)) {
            throw new IllegalArgumentException("Family-planning donations and half-allowed donations together exceed"
                    + " the qualifying limit " + limit + "; s.133(2) does not say which sums the excess is taken"
                    + " from (OQ-24), so it is refused rather than guessed (D-142)");
        }
        return counted.wholeLimited().isGreaterThan(Money.ZERO)
                ? new Counted(counted.whole(), limit, counted.half(), Money.ZERO)
                : new Counted(counted.whole(), Money.ZERO, counted.half(), limit);
    }

    private static void notInRegime(NodeContext ctx, List<Donation> donations, int firstSequence,
            List<TraceLine> lines) {
        String reason = "not in the " + ctx.regime().code().value() + " regime";
        for (boolean whole : new boolean[] {true, false}) {
            Money entered = donations.stream().filter(donation -> donation.clause().isWhole() == whole)
                    .map(Donation::amount).reduce(Money.ZERO, Money::plus);
            if (entered.isGreaterThan(Money.ZERO)) {
                RuleId row = whole ? RuleIds.DED_DONATION_WHOLE : RuleIds.DED_DONATION_HALF;
                lines.add(line(ctx, row, entered, Money.ZERO, reason, firstSequence + lines.size()));
            }
        }
    }

    private static void deduct(NodeContext ctx, RuleId row, Money counted, String group, int firstSequence,
            List<TraceLine> lines) {
        if (counted.isGreaterThan(Money.ZERO)) {
            lines.add(line(ctx, row, counted, apply(ctx, row, AMOUNT_PAID, counted), group,
                    firstSequence + lines.size()));
        }
    }

    private static Money apply(NodeContext ctx, RuleId row, BaseId base, Money amount) {
        return TemporallyApportionedCap.apply(ctx.rule(row), ctx.inputs().temporal(), CapContext.of(base, amount))
                .value();
    }

    private static TraceLine line(NodeContext ctx, RuleId row, Money in, Money out, String limitedBy, int sequence) {
        return TraceLine.of(sequence, NodeIds.CHAPTERVIII, row, StepKind.DEDUCTION, MoneyValue.of(in),
                        MoneyValue.of(out), version(ctx, row).provenance())
                .withLimitedBy(limitedBy);
    }

    private static RuleVersion version(NodeContext ctx, RuleId row) {
        return TemporallyApportionedCap.governingVersion(ctx.rule(row), ctx.inputs().temporal());
    }
}
