package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.FilingFacts;
import com.thiru.wealthlens.taxengine.input.Individual;
import com.thiru.wealthlens.taxengine.pipeline.AdvanceTaxPosition;
import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.rules.condition.ResidentialStatus;
import com.thiru.wealthlens.taxengine.rules.definition.AdvanceTaxLiability;
import com.thiru.wealthlens.taxengine.rules.definition.InstalmentSchedule;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinitions;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.reason.Reason;
import com.thiru.wealthlens.taxengine.rules.reason.ReasonCodes;
import com.thiru.wealthlens.taxengine.rules.reason.ReasonFact;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The year's advance tax and what each instalment still asks (2025 ss.403 – 406, 408; 1961 ss.207 – 211; W-089c, D-446).
 *
 * <p>The amount is the bill less tax deducted or collected at source, as s.405 / s.209 compute it, on the user's own
 * estimate (s.406 / s.210(1)); an Assessing Officer's order (s.407 / s.210(3)) cannot be entered, so it is not read. The
 * bill stands in for tax "at the rates in force in the financial year", which the pack's rates are for both years.
 *
 * <p>s.403(3)(a) / s.207(2)(a) also require no business or profession income: no node produces that head, so the condition
 * holds for everyone the engine computes. When that head is built, this node must read it.
 */
public final class AdvanceTaxNode implements ComputationNode {

    @Override
    public NodeId produces() {
        return NodeIds.ADVANCE_TAX;
    }

    @Override
    public Set<NodeId> requires() {
        return Set.of(NodeIds.TAX_NETPAYABLE);
    }

    @Override
    public Set<RuleSetId> ruleSets() {
        return Set.of(RuleSetIds.RS_ADV_LIABILITY, RuleSetIds.RS_ADV_INSTALMENTS);
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        Money bill = ctx.get(NodeIds.TAX_NETPAYABLE, MoneyValue.class).amount();
        FilingFacts filing = ctx.inputs().filing();
        Money amount = bill.minus(filing.taxAtSource()).floorAtZero();
        RuleRow liabilityRow = ctx.ruleOf(RuleSetIds.RS_ADV_LIABILITY);
        AdvanceTaxLiability liability = RuleDefinitions.require(
                liabilityRow.versionAt(ctx.inputs().temporal()).definition(), AdvanceTaxLiability.class, liabilityRow.id());
        List<TraceLine> lines = new ArrayList<>();
        TraceLine measured = ctx.traceLine(1, liabilityRow.id(), StepKind.MEASURE, bill, amount);
        Optional<NotLiable> notLiable = exemptByAge(ctx, liability)
                .or(() -> liability.reaches(amount) ? Optional.empty() : Optional.of(new NotLiable(
                        "below the amount advance tax starts at", new Reason(ReasonCodes.ADVANCE_TAX_NOT_LIABLE_BELOW,
                                Map.of("threshold", new ReasonFact.MoneyFact(liability.atLeast()))))));
        if (notLiable.isPresent()) {
            lines.add(measured.withLimitedBy("not liable: " + notLiable.get().text()).withReason(notLiable.get().reason()));
            return new NodeResult(new AdvanceTaxPosition(false, amount, List.of()), lines, List.of());
        }
        lines.add(measured.withLimitedBy("liable: the bill less tax at source")
                .withReason(new Reason(ReasonCodes.ADVANCE_TAX_LIABLE, Map.of())));
        RuleRow scheduleRow = ctx.ruleOf(RuleSetIds.RS_ADV_INSTALMENTS);
        InstalmentSchedule schedule = RuleDefinitions.require(
                scheduleRow.versionAt(ctx.inputs().temporal()).definition(), InstalmentSchedule.class, scheduleRow.id());
        List<AdvanceTaxPosition.Instalment> instalments = new ArrayList<>();
        for (InstalmentSchedule.Instalment instalment : schedule.instalments()) {
            LocalDate dueOn = inFinancialYear(ctx, instalment.dueOn());
            Money due = instalment.cumulativeShare().of(amount);
            Money paid = filing.advanceTaxPaidBy(dueOn);
            Money owed = due.minus(paid).floorAtZero();
            instalments.add(new AdvanceTaxPosition.Instalment(dueOn, due, paid, owed));
            lines.add(ctx.traceLine(lines.size() + 1, scheduleRow.id(), StepKind.MEASURE, due, owed)
                    .withLimitedBy("still owed by " + dueOn)
                    .withReason(new Reason(ReasonCodes.ADVANCE_TAX_STILL_OWED, Map.of("date", new ReasonFact.DateFact(dueOn)))));
        }
        return new NodeResult(new AdvanceTaxPosition(true, amount, instalments), lines, List.of());
    }

    /** Why a resident individual of the row's age is not liable, or empty where the person is not such a one. */
    private static Optional<NotLiable> exemptByAge(NodeContext ctx, AdvanceTaxLiability liability) {
        if (!(ctx.inputs().assessee() instanceof Individual individual)
                || individual.residence() == ResidentialStatus.NON_RESIDENT) {
            return Optional.empty();
        }
        int ageAtYearEnd = Period.between(individual.dateOfBirth(), ctx.inputs().temporal().taxYearEnd()).getYears();
        return ageAtYearEnd >= liability.exemptFromAge()
                ? Optional.of(new NotLiable("a resident individual of " + ageAtYearEnd + " with no business income",
                        new Reason(ReasonCodes.ADVANCE_TAX_NOT_LIABLE_BY_AGE,
                                Map.of("age", new ReasonFact.CountFact(ageAtYearEnd)))))
                : Optional.empty();
    }

    /** Why the person is not liable: the audit text and its reason (W-327e1, D-690). */
    private record NotLiable(String text, Reason reason) {
    }

    /** The date in this financial year that falls on {@code day}: April to December in its first calendar year, else its second. */
    static LocalDate inFinancialYear(NodeContext ctx, MonthDay day) {
        LocalDate start = ctx.inputs().temporal().taxYearStart();
        LocalDate date = day.atYear(start.getYear());
        return date.isBefore(start) ? day.atYear(start.getYear() + 1) : date;
    }
}
