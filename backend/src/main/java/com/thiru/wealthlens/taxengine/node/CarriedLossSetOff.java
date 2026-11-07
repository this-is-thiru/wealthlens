package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.RegimeVerdict;
import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsPart;
import com.thiru.wealthlens.taxengine.rules.definition.LossCarryForward;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Losses carried in from earlier years, set off against this year's income after this year's own set-off — one
 * mechanism for every carried loss (S2 ss.110, 111, 121, 202(3); 1961 ss.71B, 74, 80, 115BAC(3); D-263). A part of
 * {@link SetOffNode}, which states each ledger's losses as {@link CarriedLosses}.
 *
 * <p>The row's {@link LossCarryForward} says what the law says: the head the loss is set off against, the years it
 * lasts, and whether it must have been determined in a return filed in time. The ledger states the facts: each year's
 * loss as portions, taken in turn, each under the row that governs it and asked of the regime (D-165) — where the
 * regime forbids it, deemed given full effect to. Oldest year first. A year outside the span expires; what is left of
 * a year's loss is a MEASURE line, which next year's ledger reads (D-218).
 */
final class CarriedLossSetOff {

    /**
     * One ledger's carried losses, under the row that carries them.
     *
     * @param row the carry-forward row, whose governing version's {@code carry} this is
     * @param carry the head absorbing, the span and the return condition
     * @param texts the words its lines cite
     * @param years the loss of each year of origin, in any order
     */
    record CarriedLosses(RuleId row, LossCarryForward carry, CarriedLossTexts texts, List<CarriedLoss> years) {

        CarriedLosses {
            years = List.copyOf(years);
        }
    }

    /**
     * What is left of one year's loss.
     *
     * @param arose the tax year it arose in
     * @param determinedInReturnFiledInTime the ledger's return fact, empty where the ledger states none
     * @param portions its parts, in the order they are used
     */
    record CarriedLoss(TaxYearId arose, Optional<Boolean> determinedInReturnFiledInTime, List<Portion> portions) {

        CarriedLoss {
            portions = List.copyOf(portions);
        }

        Money remaining() {
            return portions.stream().map(Portion::amount).reduce(Money.ZERO, Money::plus);
        }
    }

    /**
     * A part of a year's loss used on its own.
     *
     * @param amount what is left of it
     * @param row the row that governs it, which its lines name and the regime is asked of
     * @param reaches the capital-gains parts it may be set off against, where the head absorbing is capital gains
     * @param setOffText a SET_OFF line's words, given the head or part that absorbed it
     */
    record Portion(Money amount, RuleId row, Predicate<CapitalGainsPart> reaches, Function<String, String> setOffText) {
    }

    /**
     * The words a ledger's lines cite, which differ by section.
     *
     * @param noun what the loss is called in a refusal ("house-property loss")
     * @param expiredCitation the sections a loss expires under
     * @param stillCarriedCitation the sections a loss is still carried under
     * @param notDeterminedInTimeCitation the sections refusing a loss not determined in a return filed in time —
     *     present exactly where the ledger states the return fact
     */
    record CarriedLossTexts(String noun, String expiredCitation, String stillCarriedCitation,
            Optional<String> notDeterminedInTimeCitation) {
    }

    private final NodeContext ctx;
    private final Map<HeadCode, Money> income;
    private final CapitalGainsAbsorption parts;
    private final List<TraceLine> lines;

    private CarriedLossSetOff(NodeContext ctx, Map<HeadCode, Money> income, CapitalGainsAbsorption parts,
            List<TraceLine> lines) {
        this.ctx = ctx;
        this.income = income;
        this.parts = parts;
        this.lines = lines;
    }

    /** Sets {@code losses} off, lowering the head's income and, for capital gains, the parts. */
    static void apply(NodeContext ctx, CarriedLosses losses, Map<HeadCode, Money> income,
            CapitalGainsAbsorption parts, List<TraceLine> lines) {
        new CarriedLossSetOff(ctx, income, parts, lines).apply(losses);
    }

    private void apply(CarriedLosses losses) {
        if (losses.carry().requiresReturnFiledInTime() && losses.texts().notDeterminedInTimeCitation().isEmpty()) {
            throw new IllegalArgumentException(losses.row() + " requires a return filed in time, but the "
                    + losses.texts().noun() + " ledger states no such fact to test; the row is a pack defect (D-263)");
        }
        TaxYearId thisYear = ctx.inputs().taxYear();
        List<CarriedLoss> oldestFirst = losses.years().stream()
                .sorted(Comparator.comparingInt(year -> year.arose().startYear())).toList();
        for (CarriedLoss year : oldestFirst) {
            if (year.arose().startYear() >= thisYear.startYear()) {
                throw new IllegalArgumentException("A " + losses.texts().noun() + " carried forward from "
                        + year.arose().label() + " cannot be brought into " + thisYear.label()
                        + "; a loss is carried only to later years");
            }
            if (year.remaining().isGreaterThan(Money.ZERO) && isCarried(losses, year, thisYear)) {
                use(losses, year);
            }
        }
    }

    /** Whether the year's loss may be used at all; where not, the line saying why. */
    private boolean isCarried(CarriedLosses losses, CarriedLoss year, TaxYearId thisYear) {
        Money remaining = year.remaining();
        String arose = year.arose().label();
        if (losses.carry().requiresReturnFiledInTime() && !year.determinedInReturnFiledInTime().orElseThrow(
                () -> new IllegalArgumentException("The " + losses.texts().noun() + " of " + arose
                        + " states no return fact, which " + losses.row() + " requires"))) {
            lines.add(ctx.traceLine(lines.size() + 1, losses.row(), StepKind.MEASURE, remaining, Money.ZERO)
                    .withLimitedBy("loss of " + arose + " not carried forward: not determined in a return filed in"
                            + " time (" + losses.texts().notDeterminedInTimeCitation().orElseThrow() + ")"));
            return false;
        }
        if (!losses.carry().span().holds(year.arose(), thisYear)) {
            lines.add(ctx.traceLine(lines.size() + 1, losses.row(), StepKind.MEASURE, remaining, remaining)
                    .withLimitedBy("loss of " + arose + " expired: carried for no more than "
                            + losses.carry().span().succeedingYears() + " tax years after it arose ("
                            + losses.texts().expiredCitation() + ")"));
            return false;
        }
        return true;
    }

    /** The year's portions in turn; what is left is still carried. */
    private void use(CarriedLosses losses, CarriedLoss year) {
        Money left = Money.ZERO;
        for (Portion portion : year.portions()) {
            if (portion.amount().isGreaterThan(Money.ZERO)) {
                left = left.plus(use(losses.carry().absorbedBy(), year.arose(), portion));
            }
        }
        if (left.isGreaterThan(Money.ZERO)) {
            lines.add(ctx.traceLine(lines.size() + 1, losses.row(), StepKind.MEASURE, year.remaining(), left)
                    .withLimitedBy("loss of " + year.arose().label() + " still carried forward ("
                            + losses.texts().stillCarriedCitation() + ")"));
        }
    }

    /** One portion: gone where the regime forbids it, else set off against the head; returns what is left of it. */
    private Money use(HeadCode absorbedBy, TaxYearId arose, Portion portion) {
        Money amount = portion.amount();
        RegimeVerdict verdict = ctx.inRegime(portion.row(),
                TemporallyApportionedCap.governingVersion(ctx.rule(portion.row()), ctx.inputs().temporal()));
        if (!verdict.applies()) {
            lines.add(ctx.traceLine(lines.size() + 1, portion.row(), StepKind.SET_OFF, amount, Money.ZERO)
                    .withLimitedBy("loss of " + arose.label() + ": " + verdict.whyNot()));
            lines.add(ctx.traceLine(lines.size() + 1, portion.row(), StepKind.MEASURE, amount, amount)
                    .withLimitedBy("loss of " + arose.label() + " deemed to have been given full effect to, and not"
                            + " carried forward (S2 s.202(3); 1961 s.115BAC(3))"));
            return Money.ZERO;
        }
        Money available = income.getOrDefault(absorbedBy, Money.ZERO);
        Money taken = absorbedBy == HeadCode.CAPITAL_GAINS
                ? parts.absorb(amount, portion.reaches(), lines, (sequence, part, gain, share) -> ctx.traceLine(
                        sequence, portion.row(), StepKind.SET_OFF, gain, share)
                        .withLimitedBy(portion.setOffText().apply(part.toString())))
                : Money.min(amount, available);
        if (taken.isGreaterThan(Money.ZERO)) {
            income.put(absorbedBy, available.minus(taken));
            if (absorbedBy != HeadCode.CAPITAL_GAINS) {
                lines.add(ctx.traceLine(lines.size() + 1, portion.row(), StepKind.SET_OFF, available, taken)
                        .withLimitedBy(portion.setOffText().apply(absorbedBy.toString())));
            }
        }
        return amount.minus(taken);
    }
}
