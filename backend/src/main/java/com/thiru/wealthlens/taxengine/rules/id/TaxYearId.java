package com.thiru.wealthlens.taxengine.rules.id;

import java.time.LocalDate;

/**
 * One tax year, identified by the calendar year it begins in: {@code TaxYearId(2026)} is TY 2026-27,
 * running from 1 April 2026 to 31 March 2027.
 *
 * <p>Every rule lookup takes one of these as its first argument, and no overload omits it. That is
 * the point: the placeholder this module replaces resolved allowance limits without a year, so a
 * value notified for one year was served for another. Forgetting the year should not compile.
 *
 * <p>The Income-tax Act 2025 uses one period — the tax year — where the 1961 Act used a previous
 * year assessed in a following assessment year. This type is the 2025-Act shape, and a 1961-Act
 * year is identified by the year its income arose in, not by its assessment year.
 */
public record TaxYearId(int startYear) {

    public static TaxYearId of(int startYear) {
        return new TaxYearId(startYear);
    }

    /**
     * The conventional label, e.g. {@code "2026-27"}.
     *
     * <p>The ending year is zero-padded, so the tax year beginning in 2099 is {@code "2099-00"} and
     * not {@code "2099-0"}. The synthetic pack used to prove the architecture's extensibility is
     * TY 2099-00, so this boundary is exercised rather than hypothetical. See the erratum at
     * {@code 02-TECH-SPEC.md} T3.
     */
    public String label() {
        return "%d-%02d".formatted(startYear, (startYear + 1) % 100);
    }

    public LocalDate startsOn() {
        return LocalDate.of(startYear, 4, 1);
    }

    public LocalDate endsOn() {
        return LocalDate.of(startYear + 1, 3, 31);
    }

    /** Whether a date falls inside this tax year, both boundaries included. */
    public boolean contains(LocalDate date) {
        return !date.isBefore(startsOn()) && !date.isAfter(endsOn());
    }

    @Override
    public String toString() {
        return label();
    }
}
