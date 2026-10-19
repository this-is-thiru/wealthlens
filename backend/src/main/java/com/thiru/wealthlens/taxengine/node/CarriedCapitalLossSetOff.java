package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.CarriedCapitalLoss;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsAbsorptionOrder;
import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsPart;
import com.thiru.wealthlens.taxengine.rules.definition.LossCarryForward;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinitions;
import com.thiru.wealthlens.taxengine.rules.definition.TaxYearSpan;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The capital losses carried in from earlier years, set off against this year's capital gains after this year's own
 * set-off (S2 s.111, s.121, s.536(2)(n); 1961 s.74, s.80; D-247) — a part of {@link SetOffNode}, kept apart because it
 * is the one set-off that works term by term.
 *
 * <p>Oldest year first; within a year the long-term loss, which only the long-term parts may take, before the
 * short-term; each against the parts in {@code SETOFF-WITHIN-CAPITAL-GAINS}'s order. Neither Act orders them: the
 * order is declared on every line. A loss is used for the eight tax years after the one it arose in and expires after
 * that; a loss not determined in a return filed in time is not carried at all. What is left of a year's loss is a
 * MEASURE line, which next year's ledger reads (D-218).
 */
final class CarriedCapitalLossSetOff {

    private static final RuleSetId ROW = RuleSetIds.RS_SETOFF_CARRIED_CAPITAL_GAINS;

    private CarriedCapitalLossSetOff() {
    }

    /** Sets the ledger's capital losses off against {@code parts}, lowering them and the head's income. */
    static void apply(NodeContext ctx, CapitalGainsAbsorptionOrder order, Map<HeadCode, Money> income,
            Map<CapitalGainsPart, Money> parts, List<TraceLine> lines) {
        GoverningRow row = GoverningRow.of(ctx, ROW);
        LossCarryForward carry = RuleDefinitions.require(row.version().definition(), LossCarryForward.class, row.id());
        if (carry.absorbedBy() != HeadCode.CAPITAL_GAINS) {
            throw new IllegalArgumentException(row.id() + " carries a capital loss against " + carry.absorbedBy()
                    + ", but this set-off works on the capital-gains parts; the row is a pack defect (D-263)");
        }
        TaxYearSpan span = carry.span();
        TaxYearId thisYear = ctx.inputs().taxYear();
        Map<TaxYearId, CarriedCapitalLoss> byYear = new TreeMap<>(Comparator.comparingInt(TaxYearId::startYear));
        byYear.putAll(ctx.inputs().ledgers().capitalLossCarriedForward());
        for (Map.Entry<TaxYearId, CarriedCapitalLoss> entry : byYear.entrySet()) {
            TaxYearId arose = entry.getKey();
            if (arose.startYear() >= thisYear.startYear()) {
                throw new IllegalArgumentException("A capital loss carried forward from " + arose.label()
                        + " cannot be brought into " + thisYear.label() + "; a loss is carried only to later years");
            }
            CarriedCapitalLoss carried = entry.getValue();
            Money remaining = carried.longTermRemaining().plus(carried.shortTermRemaining());
            if (!remaining.isGreaterThan(Money.ZERO)) {
                continue;
            }
            if (carry.requiresReturnFiledInTime() && !carried.determinedInReturnFiledInTime()) {
                lines.add(line(lines, row, StepKind.MEASURE, remaining, Money.ZERO).withLimitedBy("loss of "
                        + arose.label() + " not carried forward: not determined in a return filed in time (S2 s.121;"
                        + " 1961 s.80)"));
                continue;
            }
            if (!span.holds(arose, thisYear)) {
                lines.add(line(lines, row, StepKind.MEASURE, remaining, remaining).withLimitedBy("loss of "
                        + arose.label() + " expired: carried for no more than " + span.succeedingYears()
                        + " tax years after it arose (S2 s.111(2), s.536(2)(n); 1961 s.74(2))"));
                continue;
            }
            Money left = use(row, order, arose, carried.longTermRemaining(), true, parts, lines)
                    .plus(use(row, order, arose, carried.shortTermRemaining(), false, parts, lines));
            Money taken = remaining.minus(left);
            income.put(carry.absorbedBy(), income.getOrDefault(carry.absorbedBy(), Money.ZERO).minus(taken));
            if (left.isGreaterThan(Money.ZERO)) {
                lines.add(line(lines, row, StepKind.MEASURE, remaining, left).withLimitedBy("loss of "
                        + arose.label() + " still carried forward (S2 s.111(1)(b); 1961 s.74(1)(c))"));
            }
        }
    }

    /** One term of one year's loss against the parts it may take, in order; returns what is left of it. */
    private static Money use(GoverningRow row, CapitalGainsAbsorptionOrder order, TaxYearId arose, Money loss,
            boolean longTerm, Map<CapitalGainsPart, Money> parts, List<TraceLine> lines) {
        Money remaining = loss;
        for (CapitalGainsPart part : order.parts()) {
            Money available = parts.getOrDefault(part, Money.ZERO);
            if (!remaining.isGreaterThan(Money.ZERO) || !available.isGreaterThan(Money.ZERO)
                    || longTerm && !part.isLongTerm()) {
                continue;
            }
            Money taken = Money.min(remaining, available);
            parts.put(part, available.minus(taken));
            remaining = remaining.minus(taken);
            lines.add(line(lines, row, StepKind.SET_OFF, available, taken).withLimitedBy((longTerm ? "long" : "short")
                    + "-term loss of " + arose.label() + " against " + part + "; order: this year's set-off first, then"
                    + " the oldest year, its long-term loss first, the parts " + order.parts() + " — the declared"
                    + " default, the Acts prescribing none (D-247)"));
        }
        return remaining;
    }

    private static TraceLine line(List<TraceLine> lines, GoverningRow row, StepKind kind, Money in, Money out) {
        return TraceLine.of(lines.size() + 1, NodeIds.SETOFF, row.id(), kind, MoneyValue.of(in), MoneyValue.of(out),
                row.version().provenance());
    }
}
