package com.thiru.wealthlens.shared.util.time;

import java.time.LocalDate;
import java.time.Month;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One Indian financial year, 1 April to 31 March inclusive, identified by the calendar year it begins in — the one
 * place outside the tax engine where a date becomes a year (W-161, D-374).
 *
 * <p>Its label, {@code "2025-2026"}, is the form {@code portfolio} stores (W-086a). The tax engine keeps
 * {@code TaxYearId} for the same period (D-269): the engine imports nothing from {@code shared}, and doing so would
 * be the first step of a module cycle ({@code shared} depends on {@code portfolio}), so the boundary is stated here
 * and there, nowhere else.
 *
 * @param startYear the calendar year the financial year begins in
 */
public record FinancialYear(int startYear) {

    private static final Pattern LABEL = Pattern.compile("(\\d{4})-(\\d{4})");

    /** The financial year {@code date} falls in; 31 March belongs to the year ending, not the next. */
    public static FinancialYear containing(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("The financial year containing a date needs the date");
        }
        FinancialYear startingThatCalendarYear = new FinancialYear(date.getYear());
        return date.isBefore(startingThatCalendarYear.startsOn()) ? new FinancialYear(date.getYear() - 1)
                : startingThatCalendarYear;
    }

    /**
     * The year a stored label names.
     *
     * @throws IllegalArgumentException unless the label is two consecutive years, such as {@code 2025-2026}
     */
    public static FinancialYear parse(String label) {
        return ofLabel(label).orElseThrow(() -> new IllegalArgumentException("A financial year is written as portfolio"
                + " writes it, such as 2025-2026, but was " + label));
    }

    /** The year a stored label names, or none for a label that is not two consecutive years. */
    public static Optional<FinancialYear> ofLabel(String label) {
        Matcher matcher = LABEL.matcher(label == null ? "" : label);
        if (!matcher.matches() || Integer.parseInt(matcher.group(2)) != Integer.parseInt(matcher.group(1)) + 1) {
            return Optional.empty();
        }
        return Optional.of(new FinancialYear(Integer.parseInt(matcher.group(1))));
    }

    public LocalDate startsOn() {
        return LocalDate.of(startYear, Month.APRIL, 1);
    }

    public LocalDate endsOn() {
        return LocalDate.of(startYear + 1, Month.MARCH, 31);
    }

    /** {@code "2025-2026"}, the stored form. */
    public String label() {
        return startYear + "-" + (startYear + 1);
    }

    /** Where a month falls in a financial year: April 0, March 11. */
    public static int positionOf(Month month) {
        return (month.getValue() - Month.APRIL.getValue() + 12) % 12;
    }
}
