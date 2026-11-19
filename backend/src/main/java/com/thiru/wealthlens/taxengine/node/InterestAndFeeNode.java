package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.FilingFacts;
import com.thiru.wealthlens.taxengine.pipeline.AdvanceTaxPosition;
import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import com.thiru.wealthlens.taxengine.pipeline.InterestAndFees;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.rules.definition.AdvanceShortfallInterest;
import com.thiru.wealthlens.taxengine.rules.definition.DefermentInterest;
import com.thiru.wealthlens.taxengine.rules.definition.InterestCalculation;
import com.thiru.wealthlens.taxengine.rules.definition.LateReturnFee;
import com.thiru.wealthlens.taxengine.rules.definition.MonthlyInterest;
import com.thiru.wealthlens.taxengine.rules.definition.RefundInterest;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinition;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinitions;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.reason.Reason;
import com.thiru.wealthlens.taxengine.rules.reason.ReasonCodes;
import com.thiru.wealthlens.taxengine.rules.reason.ReasonFact;
import com.thiru.wealthlens.taxengine.rules.registry.Provenance;
import com.thiru.wealthlens.taxengine.trace.Notice;
import com.thiru.wealthlens.taxengine.trace.Severity;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceAnnotation;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Interest on the return and the late-return fee (2025 ss.423 – 425, 428; 1961 ss.234A – 234C, 234F; Rules 2026 r.269;
 * W-112, D-446). One line per provision, and one per date for deferment.
 *
 * <p>The three interests are measured on three different amounts, as the sections define them: late filing on tax less
 * advance tax and tax at source; the shortfall and deferment on tax less tax at source only. The period ends on the date the
 * return was furnished, or, for a return not yet furnished, on the as-of date, as if it were furnished and paid that day
 * (D-446 C2a). Deferment carries the s.425(4) / s.234C relief for income arising after a date ({@link DefermentRelief};
 * W-112a). Interest added to a refund is a receivable, never owed (s.437 / s.244A; W-112b). Not computed, and said so where
 * the figure is shown: an Assessing Officer's order, interest on a refund later found not due (s.426 / s.234D).
 */
public final class InterestAndFeeNode implements ComputationNode {

    @Override
    public NodeId produces() {
        return NodeIds.INTEREST_AND_FEES;
    }

    @Override
    public Set<NodeId> requires() {
        return Set.of(NodeIds.TAX_NETPAYABLE, NodeIds.ADVANCE_TAX, NodeIds.ROUND_TOTALINCOME);
    }

    @Override
    public Set<RuleSetId> ruleSets() {
        return Set.of(RuleSetIds.RS_INT_LATE_RETURN, RuleSetIds.RS_INT_ADVANCE_SHORTFALL, RuleSetIds.RS_INT_DEFERMENT,
                RuleSetIds.RS_INT_CALCULATION, RuleSetIds.RS_FEE_LATE_RETURN, RuleSetIds.RS_INT_REFUND);
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        Run run = new Run(ctx, ctx.get(NodeIds.TAX_NETPAYABLE, MoneyValue.class).amount(),
                ctx.get(NodeIds.ADVANCE_TAX, AdvanceTaxPosition.class),
                definition(ctx, RuleSetIds.RS_INT_CALCULATION, InterestCalculation.class),
                calculationNotice(ctx));
        run.lateReturn();
        run.shortfall();
        run.deferment();
        run.fee(ctx.get(NodeIds.ROUND_TOTALINCOME, MoneyValue.class).amount());
        run.refund();
        return new NodeResult(new InterestAndFees(run.items, run.reliefPaidBy), run.lines, run.annotations);
    }

    private static <T extends RuleDefinition> T definition(NodeContext ctx, RuleSetId set, Class<T> type) {
        RuleRow row = ctx.ruleOf(set);
        return RuleDefinitions.require(row.versionAt(ctx.inputs().temporal()).definition(), type, row.id());
    }

    /** What every interest line must say when the calculation row is not confirmed against its source (D-446 C4a). */
    private static List<Notice> calculationNotice(NodeContext ctx) {
        RuleRow row = ctx.ruleOf(RuleSetIds.RS_INT_CALCULATION);
        Provenance provenance = row.versionAt(ctx.inputs().temporal()).provenance();
        return provenance.requiresUserVisibleFlag()
                ? List.of(new Notice(Severity.UNVERIFIED_RULE, "How this interest is rounded is not confirmed: "
                        + provenance.whatToCheck(), List.of(row.id())))
                : List.of();
    }

    /**
     * Months in a period from {@code from} to {@code to}, both included; a fraction counted whole or ignored as the
     * calculation row says (r.269(a), (b)). Nil where the period has not begun.
     */
    static int months(LocalDate from, LocalDate to, boolean fractionCountsWhole) {
        if (to.isBefore(from)) {
            return 0;
        }
        Period period = Period.between(from, to.plusDays(1));
        int whole = period.getYears() * 12 + period.getMonths();
        return fractionCountsWhole && period.getDays() > 0 ? whole + 1 : whole;
    }

    /** One evaluation's running state: the lines, items and annotations each provision adds. */
    private static final class Run {

        private final NodeContext ctx;
        private final FilingFacts filing;
        private final Money bill;
        private final AdvanceTaxPosition advance;
        private final InterestCalculation calculation;
        private final List<Notice> calculationNotice;
        private final LocalDate end;
        private final Money advancePaid;
        private final List<TraceLine> lines = new ArrayList<>();
        private final List<InterestAndFees.Item> items = new ArrayList<>();
        private final List<TraceAnnotation> annotations = new ArrayList<>();
        private Optional<LocalDate> reliefPaidBy = Optional.empty();

        Run(NodeContext ctx, Money bill, AdvanceTaxPosition advance, InterestCalculation calculation,
                List<Notice> calculationNotice) {
            this.ctx = ctx;
            this.filing = ctx.inputs().filing();
            this.bill = bill;
            this.advance = advance;
            this.calculation = calculation;
            this.calculationNotice = calculationNotice;
            this.end = filing.furnishedOn().orElse(ctx.inputs().asOfDate());
            this.advancePaid = filing.advanceTaxPaidBy(ctx.inputs().temporal().taxYearEnd());
        }

        /** s.423 / s.234A: from the day after the due date to the end, on tax less advance tax and tax at source. */
        void lateReturn() {
            RuleRow row = ctx.ruleOf(RuleSetIds.RS_INT_LATE_RETURN);
            MonthlyInterest rule = definition(ctx, RuleSetIds.RS_INT_LATE_RETURN, MonthlyInterest.class);
            LocalDate from = filing.dueDate().plusDays(1);
            Money base = calculation.base(bill.minus(advancePaid).minus(filing.taxAtSource()));
            int months = months(from, end, calculation.fractionOfMonthCountsWhole());
            Money interest = rule.perMonth().of(base).times(months);
            add(row.id(), InterestAndFees.Kind.INTEREST, base, interest, period(months, from, end),
                    periodReason(months, from, end));
            if (interest.isGreaterThan(Money.ZERO) && !rule.statesPartOfMonth() && months != months(from, end, false)) {
                annotate(new Notice(Severity.ATTENTION, "The section counts months and does not say part of a month; a part"
                        + " month is counted as a whole month, as the sections beside it do (OQ-39)", List.of(row.id())));
            }
        }

        /**
         * s.424 / s.234B: from the first day after the tax year, on the shortfall of advance tax from the assessed tax; a
         * payment on the return stops it on what it pays, from its date (s.424(4) / s.234B(2)). Months already counted are
         * not counted again on the smaller shortfall.
         */
        void shortfall() {
            RuleRow row = ctx.ruleOf(RuleSetIds.RS_INT_ADVANCE_SHORTFALL);
            AdvanceShortfallInterest rule = definition(ctx, RuleSetIds.RS_INT_ADVANCE_SHORTFALL, AdvanceShortfallInterest.class);
            Money assessed = bill.minus(filing.taxAtSource()).floorAtZero();
            if (!advance.liable() || !rule.runsOn(advancePaid, assessed)) {
                add(row.id(), InterestAndFees.Kind.INTEREST, assessed, Money.ZERO, advance.liable()
                        ? "advance tax paid reaches the share of the assessed tax" : "not liable to advance tax",
                        new Reason(advance.liable() ? ReasonCodes.SHORTFALL_ADVANCE_TAX_PAID_ENOUGH
                                : ReasonCodes.INTEREST_NOT_LIABLE_TO_ADVANCE_TAX, Map.of()));
                return;
            }
            LocalDate from = ctx.inputs().temporal().taxYearEnd().plusDays(1);
            Money shortfall = assessed.minus(advancePaid);
            Money interest = Money.ZERO;
            int counted = 0;
            List<FilingFacts.Payment> payments = filing.selfAssessmentPaid().stream()
                    .filter(p -> !p.paidOn().isAfter(end)).sorted(Comparator.comparing(FilingFacts.Payment::paidOn)).toList();
            for (FilingFacts.Payment payment : payments) {
                int upTo = months(from, payment.paidOn(), calculation.fractionOfMonthCountsWhole());
                interest = interest.plus(rule.perMonth().of(calculation.base(shortfall)).times(upTo - counted));
                counted = Math.max(counted, upTo);
                shortfall = shortfall.minus(payment.amount());
            }
            int total = Math.max(counted, months(from, end, calculation.fractionOfMonthCountsWhole()));
            interest = interest.plus(rule.perMonth().of(calculation.base(shortfall)).times(total - counted));
            add(row.id(), InterestAndFees.Kind.INTEREST, calculation.base(assessed.minus(advancePaid)), interest,
                    period(total, from, end), periodReason(total, from, end));
        }

        /** The months and their span as a reason, or the day it runs from where it has not begun (W-327e1, D-690). */
        private static Reason periodReason(int months, LocalDate from, LocalDate end) {
            return end.isBefore(from)
                    ? new Reason(ReasonCodes.INTEREST_NOT_YET_DUE, Map.of("from", new ReasonFact.DateFact(from)))
                    : new Reason(ReasonCodes.INTEREST_FOR_MONTHS, Map.of("months", new ReasonFact.CountFact(months),
                            "from", new ReasonFact.DateFact(from), "to", new ReasonFact.DateFact(end)));
        }

        /** The months counted and over what span; a span not yet begun says so rather than running backwards (W-142a). */
        private static String period(int months, LocalDate from, LocalDate end) {
            return end.isBefore(from) ? "not yet due: runs from " + from : months + " months from " + from + " to " + end;
        }

        /** s.425 / s.234C: at each date already reached, on the shortfall from the share of the tax due on the returned income. */
        void deferment() {
            RuleRow row = ctx.ruleOf(RuleSetIds.RS_INT_DEFERMENT);
            DefermentInterest rule = definition(ctx, RuleSetIds.RS_INT_DEFERMENT, DefermentInterest.class);
            Money due = bill.minus(filing.taxAtSource()).floorAtZero();
            DefermentRelief relief = new DefermentRelief(ctx, rule, due);
            Money total = Money.ZERO;
            for (DefermentInterest.Step step : rule.steps()) {
                LocalDate dueOn = AdvanceTaxNode.inFinancialYear(ctx, step.dueOn());
                Money paid = filing.advanceTaxPaidBy(dueOn);
                boolean reached = !dueOn.isAfter(end);
                DefermentRelief.AtDate relieved = advance.liable() && reached ? relief.at(dueOn) : DefermentRelief.AtDate.NONE;
                if (relieved.reason().isPresent()) {
                    reliefPaidBy = Optional.of(relief.paidBy());
                }
                Money dueHere = due.minus(relieved.leftOut());
                boolean harboured = step.noInterestFrom().filter(share -> !paid.isLessThan(share.of(dueHere))).isPresent();
                Money base = calculation.base(step.share().of(dueHere).minus(paid));
                Money interest = advance.liable() && reached && !harboured ? step.interest().of(base) : Money.ZERO;
                total = total.plus(interest);
                String why = deferredBecause(reached, harboured, dueOn);
                lines.add(ctx.traceLine(lines.size() + 1, row.id(), StepKind.INTEREST, base, interest)
                        .withLimitedBy(relieved.reason().map(reason -> why + "; " + reason).orElse(why))
                        .withReason(deferredReason(reached, harboured, dueOn, relieved, relief.paidBy())));
                flagIfCharged(interest);
            }
            if (total.isGreaterThan(Money.ZERO)) {
                items.add(new InterestAndFees.Item(row.id(), InterestAndFees.Kind.INTEREST, total));
            }
            if (total.isGreaterThan(Money.ZERO) && relief.hasUndatedDividend()) {
                annotate(new Notice(Severity.ATTENTION, "A dividend entered without the date it was received cannot be"
                        + " relieved; less, or none, may be due if it arose after an instalment date and its tax was paid"
                        + " later", List.of(row.id())));
            }
        }

        /** A deferment date's reason, with its relief where relieved income arose after it (W-327e1 C1a, D-690). */
        private Reason deferredReason(boolean reached, boolean harboured, LocalDate dueOn, DefermentRelief.AtDate relieved,
                LocalDate paidBy) {
            if (!advance.liable()) {
                return new Reason(ReasonCodes.INTEREST_NOT_LIABLE_TO_ADVANCE_TAX, Map.of());
            }
            ReasonFact date = new ReasonFact.DateFact(dueOn);
            if (!reached) {
                return new Reason(ReasonCodes.DEFERMENT_NOT_YET_REACHED, Map.of("date", date));
            }
            if (!relieved.arose()) {
                return new Reason(harboured ? ReasonCodes.DEFERMENT_PAID_ENOUGH : ReasonCodes.DEFERMENT_SHORT,
                        Map.of("date", date));
            }
            ReasonFact by = new ReasonFact.DateFact(paidBy);
            if (relieved.relieved()) {
                return new Reason(harboured ? ReasonCodes.DEFERMENT_PAID_ENOUGH_RELIEVED : ReasonCodes.DEFERMENT_SHORT_RELIEVED,
                        Map.of("date", date, "relieved", new ReasonFact.MoneyFact(relieved.leftOut()), "paidBy", by));
            }
            return new Reason(harboured ? ReasonCodes.DEFERMENT_PAID_ENOUGH_NOT_RELIEVED
                    : ReasonCodes.DEFERMENT_SHORT_NOT_RELIEVED, Map.of("date", date, "tax",
                            new ReasonFact.MoneyFact(relieved.arising()), "paidBy", by, "paid",
                            new ReasonFact.MoneyFact(relieved.paidAfter())));
        }

        /** What a deferment date's line says of it, before any relief. */
        private String deferredBecause(boolean reached, boolean harboured, LocalDate dueOn) {
            if (!advance.liable()) {
                return "not liable to advance tax";
            }
            if (!reached) {
                return "by " + dueOn + ", not yet reached";
            }
            return harboured ? "by " + dueOn + ", paid enough" : "short by " + dueOn;
        }

        /** s.428 / s.234F: a fee where the return is furnished after its due date, on the total income. */
        void fee(Money totalIncome) {
            RuleRow row = ctx.ruleOf(RuleSetIds.RS_FEE_LATE_RETURN);
            LateReturnFee rule = definition(ctx, RuleSetIds.RS_FEE_LATE_RETURN, LateReturnFee.class);
            boolean late = end.isAfter(filing.dueDate());
            Money fee = late ? rule.feeFor(totalIncome) : Money.ZERO;
            lines.add(ctx.traceLine(lines.size() + 1, row.id(), StepKind.FEE, totalIncome, fee)
                    .withLimitedBy(late ? "furnished after " + filing.dueDate() : "furnished by " + filing.dueDate())
                    .withReason(new Reason(late ? ReasonCodes.FEE_RETURN_FURNISHED_LATE
                            : ReasonCodes.FEE_RETURN_FURNISHED_IN_TIME, Map.of("date", new ReasonFact.DateFact(filing.dueDate())))));
            if (late) {
                items.add(new InterestAndFees.Item(row.id(), InterestAndFees.Kind.FEE, fee));
                annotate(new Notice(Severity.ATTENTION, "The fee falls only on a person required to furnish a return; whether"
                        + " you are is not decided here, so it is shown as if you were", List.of(row.id())));
            }
            if (late && rule.isCeilingFor(totalIncome)) {
                annotate(new Notice(Severity.ATTENTION, "The Act sets this fee as an upper limit; the limit is shown",
                        List.of(row.id())));
            }
        }

        /**
         * s.437 / s.244A: interest the Government adds to the refund this computation shows — tax paid less the bill and
         * what this node charges — to its grant, or to the as-of date as if granted then (W-112b C1a, C2a). The refund
         * comes first out of tax paid on the return, the latest payment first, then out of tax at source and advance tax
         * (C3a): the sections do not order them, and this never overstates the interest. Kept out of what is owed (C4a).
         */
        void refund() {
            Money owed = items.stream().map(InterestAndFees.Item::amount).reduce(Money.ZERO, Money::plus);
            List<FilingFacts.Payment> onReturn = filing.selfAssessmentPaid().stream()
                    .sorted(Comparator.comparing(FilingFacts.Payment::paidOn).reversed()).toList();
            Money paid = filing.taxAtSource().plus(advancePaid)
                    .plus(onReturn.stream().map(FilingFacts.Payment::amount).reduce(Money.ZERO, Money::plus));
            Money refund = paid.minus(bill).minus(owed);
            if (!refund.isGreaterThan(Money.ZERO)) {
                return;
            }
            RuleRow row = ctx.ruleOf(RuleSetIds.RS_INT_REFUND);
            RefundInterest rule = definition(ctx, RuleSetIds.RS_INT_REFUND, RefundInterest.class);
            LocalDate granted = filing.refundGrantedOn().orElse(ctx.inputs().asOfDate());
            String until = filing.refundGrantedOn().isPresent() ? "granted on " + granted : "as if granted on " + granted;
            if (refund.isLessThan(rule.noInterestBelowShare().of(bill))) {
                add(row.id(), InterestAndFees.Kind.RECEIVABLE, calculation.base(refund), Money.ZERO, "a refund of " + refund
                        + " is less than " + rule.noInterestBelowShare() + " of the tax of " + bill + ": none",
                        new Reason(ReasonCodes.REFUND_BELOW_SHARE, Map.of("refund", new ReasonFact.MoneyFact(refund),
                                "share", new ReasonFact.ShareFact(rule.noInterestBelowShare()),
                                "tax", new ReasonFact.MoneyFact(bill))));
                return;
            }
            Money left = refund;
            for (FilingFacts.Payment payment : onReturn) {
                Money slice = Money.min(left, payment.amount());
                if (slice.isGreaterThan(Money.ZERO)) {
                    receivable(row, rule, slice, latest(end, payment.paidOn()), granted,
                            "out of tax paid on the return on " + payment.paidOn() + ", " + until,
                            Optional.of(payment.paidOn()));
                }
                left = left.minus(slice);
            }
            if (left.isGreaterThan(Money.ZERO)) {
                LocalDate from = end.isAfter(filing.dueDate()) ? end : ctx.inputs().temporal().taxYearEnd().plusDays(1);
                receivable(row, rule, left, from, granted, "out of tax at source and advance tax, " + until, Optional.empty());
            }
            refundNotices(row, !onReturn.isEmpty());
        }

        /** Interest on one slice of a refund; {@code paidOn} is the return payment it returns, empty for prepaid tax. */
        private void receivable(RuleRow row, RefundInterest rule, Money slice, LocalDate from, LocalDate granted, String what,
                Optional<LocalDate> paidOn) {
            Money base = calculation.base(slice);
            int months = months(from, granted, calculation.fractionOfMonthCountsWhole());
            add(row.id(), InterestAndFees.Kind.RECEIVABLE, base, rule.perMonth().of(base).times(months),
                    period(months, from, granted) + "; " + what, refundReason(months, from, granted, paidOn));
        }

        /** A refund slice's reason: its months and span, and the payment it returns (W-327e1 C2a, D-690). */
        private static Reason refundReason(int months, LocalDate from, LocalDate granted, Optional<LocalDate> paidOn) {
            if (granted.isBefore(from)) {
                return periodReason(months, from, granted);
            }
            Map<String, ReasonFact> facts = new HashMap<>(Map.of("months", new ReasonFact.CountFact(months),
                    "from", new ReasonFact.DateFact(from), "to", new ReasonFact.DateFact(granted)));
            paidOn.ifPresent(day -> facts.put("paidOn", new ReasonFact.DateFact(day)));
            return new Reason(paidOn.isPresent() ? ReasonCodes.REFUND_INTEREST_ON_RETURN_PAYMENT
                    : ReasonCodes.REFUND_INTEREST_ON_PREPAID_TAX, facts);
        }

        private void refundNotices(RuleRow row, boolean outOfTaxOnTheReturn) {
            annotate(new Notice(Severity.ATTENTION, "Interest on a refund is income of the year it is received in, under"
                    + " other sources; it is not in this year's figures", List.of(row.id())));
            if (outOfTaxOnTheReturn) {
                annotate(new Notice(Severity.ATTENTION, "The Act does not say which payment a refund returns first; tax paid"
                        + " on the return is taken first, which never overstates the interest", List.of(row.id())));
            }
        }

        private static LocalDate latest(LocalDate one, LocalDate other) {
            return one.isAfter(other) ? one : other;
        }

        private void add(RuleId rule, InterestAndFees.Kind kind, Money base, Money amount, String why, Reason reason) {
            lines.add(ctx.traceLine(lines.size() + 1, rule, StepKind.INTEREST, base, amount).withLimitedBy(why)
                    .withReason(reason));
            if (amount.isGreaterThan(Money.ZERO)) {
                items.add(new InterestAndFees.Item(rule, kind, amount));
            }
            flagIfCharged(amount);
        }

        private void flagIfCharged(Money interest) {
            if (interest.isGreaterThan(Money.ZERO) && !calculationNotice.isEmpty()) {
                annotations.add(new TraceAnnotation(lines.getLast().sequence(), calculationNotice));
            }
        }

        private void annotate(Notice notice) {
            annotations.add(TraceAnnotation.of(lines.getLast().sequence(), notice));
        }
    }
}
