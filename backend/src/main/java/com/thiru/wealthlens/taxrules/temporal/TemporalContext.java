package com.thiru.wealthlens.taxrules.temporal;

import com.thiru.wealthlens.taxrules.id.TaxYearId;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/**
 * The temporal coordinate one computation carries. Assembled once, per computation.
 *
 * <p>{@code D-YEARKEY} — key everything on the tax year — is the common case, not the rule. s.197(3)
 * turns on whether land was acquired before 23 July 2024, so a computation made decades from now
 * still needs that date; loan-sanction windows turn on when the loan was sanctioned; late-filing
 * interest turns on when the return was filed. Each rule declares its {@link TemporalAxis}, and this
 * supplies the date for it.
 *
 * @param taxYear the year being computed
 * @param taxYearStart 1 April of that year
 * @param taxYearEnd 31 March following
 * @param asOfDate when this computation is being made — distinct from the year, and what makes a
 *     filing position date-dependent while the computation itself is not
 * @param axisDates per-fact dates supplied by the node that needs them
 */
public record TemporalContext(
        TaxYearId taxYear,
        LocalDate taxYearStart,
        LocalDate taxYearEnd,
        LocalDate asOfDate,
        Map<TemporalAxis, LocalDate> axisDates) {

    public TemporalContext {
        if (taxYear == null || taxYearStart == null || taxYearEnd == null || asOfDate == null) {
            throw new IllegalArgumentException("A TemporalContext needs a year, its bounds and an as-of date");
        }
        if (axisDates == null) {
            throw new IllegalArgumentException("axisDates may not be null; pass an empty map instead");
        }
        if (!taxYearStart.isBefore(taxYearEnd)) {
            throw new IllegalArgumentException(
                    "Tax year " + taxYear + " starts " + taxYearStart + " and ends " + taxYearEnd);
        }
        axisDates = Map.copyOf(axisDates);
    }

    /** A year-keyed context, for the common case where no rule needs a per-fact date. */
    public static TemporalContext forTaxYear(TaxYearId taxYear, LocalDate asOfDate) {
        return new TemporalContext(
                taxYear, taxYear.startsOn(), taxYear.endsOn(), asOfDate, Map.of());
    }

    /**
     * The date to read a rule against.
     *
     * <p>A year-keyed rule reads at year end, which is when the year's law is settled. Every other
     * axis must have been supplied.
     *
     * @throws MissingTemporalCoordinateException if a non-year axis has no date — see that class
     */
    public LocalDate on(TemporalAxis axis) {
        if (axis == TemporalAxis.TAX_YEAR) {
            return taxYearEnd;
        }
        return Optional.ofNullable(axisDates.get(axis))
                .orElseThrow(() -> new MissingTemporalCoordinateException(axis));
    }

    /** The same context with one axis date supplied. */
    public TemporalContext with(TemporalAxis axis, LocalDate date) {
        Map<TemporalAxis, LocalDate> merged = new java.util.EnumMap<>(TemporalAxis.class);
        merged.putAll(axisDates);
        merged.put(axis, date);
        return new TemporalContext(taxYear, taxYearStart, taxYearEnd, asOfDate, merged);
    }
}
