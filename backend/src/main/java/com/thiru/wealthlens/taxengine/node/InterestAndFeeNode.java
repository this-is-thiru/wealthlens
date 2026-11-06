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
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinition;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinitions;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
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
import java.util.List;
import java.util.Set;

/**
 * Interest on the return and the late-return fee (2025 ss.423 – 425, 428; 1961 ss.234A – 234C, 234F; Rules 2026 r.269;
 * W-112, D-446). One line per provision, and one per date for deferment.
 *
 * <p>The three interests are measured on three different amounts, as the sections define them: late filing on tax less
 * advance tax and tax at source; the shortfall and deferment on tax less tax at source only. The period ends on the date the
 * return was furnished, or, for a return not yet furnished, on the as-of date, as if it were furnished and paid that day
 * (D-446 C2a). Not computed, and said so where the figure is shown: the s.425(4) / s.234C relief for capital gains and
 * dividends (W-112a), an Assessing Officer's order, interest on a refund (W-112b).
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
                RuleSetIds.RS_INT_CALCULATION, RuleSetIds.RS_FEE_LATE_RETURN);
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
        return new NodeResult(new InterestAndFees(run.items), run.lines, run.annotations);
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
            add(row.id(), InterestAndFees.Kind.INTEREST, base, interest, months + " months from " + from + " to " + end);
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
                        ? "advance tax paid reaches the share of the assessed tax" : "not liable to advance tax");
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
                    total + " months from " + from + " to " + end);
        }

        /** s.425 / s.234C: at each date already reached, on the shortfall from the share of the tax due on the returned income. */
        void deferment() {
            RuleRow row = ctx.ruleOf(RuleSetIds.RS_INT_DEFERMENT);
            DefermentInterest rule = definition(ctx, RuleSetIds.RS_INT_DEFERMENT, DefermentInterest.class);
            Money due = bill.minus(filing.taxAtSource()).floorAtZero();
            Money total = Money.ZERO;
            for (DefermentInterest.Step step : rule.steps()) {
                LocalDate dueOn = AdvanceTaxNode.inFinancialYear(ctx, step.dueOn());
                Money paid = filing.advanceTaxPaidBy(dueOn);
                boolean reached = !dueOn.isAfter(end);
                boolean harboured = step.noInterestFrom().filter(share -> !paid.isLessThan(share.of(due))).isPresent();
                Money base = calculation.base(step.share().of(due).minus(paid));
                Money interest = advance.liable() && reached && !harboured ? step.interest().of(base) : Money.ZERO;
                total = total.plus(interest);
                lines.add(ctx.traceLine(lines.size() + 1, row.id(), StepKind.INTEREST, base, interest).withLimitedBy(
                        !advance.liable() ? "not liable to advance tax" : !reached ? "by " + dueOn + ", not yet reached"
                                : harboured ? "by " + dueOn + ", paid enough" : "short by " + dueOn));
                flagIfCharged(interest);
            }
            if (total.isGreaterThan(Money.ZERO)) {
                items.add(new InterestAndFees.Item(row.id(), InterestAndFees.Kind.INTEREST, total));
                annotate(new Notice(Severity.ATTENTION, "Less, or none, may be due where the shortfall comes from capital"
                        + " gains or dividends whose tax was paid in a later instalment; that relief is not computed",
                        List.of(row.id())));
            }
        }

        /** s.428 / s.234F: a fee where the return is furnished after its due date, on the total income. */
        void fee(Money totalIncome) {
            RuleRow row = ctx.ruleOf(RuleSetIds.RS_FEE_LATE_RETURN);
            LateReturnFee rule = definition(ctx, RuleSetIds.RS_FEE_LATE_RETURN, LateReturnFee.class);
            boolean late = end.isAfter(filing.dueDate());
            Money fee = late ? rule.feeFor(totalIncome) : Money.ZERO;
            lines.add(ctx.traceLine(lines.size() + 1, row.id(), StepKind.FEE, totalIncome, fee)
                    .withLimitedBy(late ? "furnished after " + filing.dueDate() : "furnished by " + filing.dueDate()));
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

        private void add(RuleId rule, InterestAndFees.Kind kind, Money base, Money amount, String why) {
            lines.add(ctx.traceLine(lines.size() + 1, rule, StepKind.INTEREST, base, amount).withLimitedBy(why));
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
