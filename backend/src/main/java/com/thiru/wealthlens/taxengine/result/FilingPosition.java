package com.thiru.wealthlens.taxengine.result;

import com.thiru.wealthlens.taxrules.money.Money;
import java.time.LocalDate;

/**
 * The part of the answer that moves with the date.
 *
 * <h2>Thin, cheap, recomputed freely</h2>
 *
 * <p>{@code 23-architecture-consolidation.md} A6. {@link TaxComputation} is cached indefinitely on
 * a key with no date in it; this is the overlay, keyed on that plus {@link #asOf} truncated to the
 * day. The overlay is a handful of nodes rather than thirty-one, which is what makes {@code F29}'s
 * forecast and {@code F30}'s calendar affordable at all.
 *
 * <p>{@code asOf} is an <b>input</b>, never ambient. {@code taxengine} reads no clock — a clock
 * inside would make a golden test time-dependent and an audit irreproducible, and the design has
 * rules keyed on filing date that must be supplied rather than inferred from today.
 *
 * <h2>This record will grow</h2>
 *
 * <p>A6 also lists {@code ItrSelection form} (form selection moves with the filing date,
 * {@code 21}), {@code InterestBreakdown interestAndFees} ({@code C1}, accrues by month) and
 * {@code List<CalendarEntry> calendar} ({@code F30}). None of the three types exists:
 * {@code InterestBreakdown} belongs to {@code W-112} (Phase 12), and {@code ItrSelection} and
 * {@code CalendarEntry} are named in A6 and <b>nowhere else in the design or the backlog</b>. Each
 * arrives with the item that owns its type (decision <b>D-032</b>), the way {@code CapShape} and
 * {@code NodeValue} grew.
 *
 * <p>What is here is enough to carry the split: {@link #asOf} is the date-dependent field, and
 * {@link TaxComputation} has none.
 *
 * @param basedOn the computation this overlays
 * @param asOf the date this position is stated as at — an input, not a clock reading
 * @param netPayable the statutory bill plus filing effects
 */
public record FilingPosition(ComputationId basedOn, LocalDate asOf, Money netPayable) {

    public FilingPosition {
        if (basedOn == null) {
            throw new IllegalArgumentException(
                    "A FilingPosition must name the computation it overlays; without it the figure"
                            + " cannot be explained or reproduced");
        }
        if (asOf == null) {
            throw new IllegalArgumentException(
                    "A FilingPosition must carry its asOf date. The engine reads no clock, so this"
                            + " arrives as an input -- defaulting it to today is what makes an audit"
                            + " irreproducible");
        }
        if (netPayable == null) {
            throw new IllegalArgumentException("A FilingPosition must carry a net payable");
        }
    }
}
