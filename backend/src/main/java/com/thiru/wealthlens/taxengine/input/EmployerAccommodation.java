package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;
import java.util.Optional;

/**
 * Residential accommodation an employer provided in the year (S2 s.17(1)(a)–(b); 1961 s.17(2)(i)–(ii); S4 r.15(2)
 * Table I; Rules 1962 r.3(1); D-111).
 *
 * <p>What is entered is what the Table reads: who provides it and on what footing, the period occupied, the city's
 * 2011-census band for owned accommodation (the Rules name no cities), the employer's cost for the period — the
 * licence fee, the lease rental or the hotel charges — the rent the employee paid, and the furniture. The first tax
 * year it was provided decides whether the Cost Inflation Index cap applies (r.15(2)(d); r.3(1) third proviso).
 *
 * @param employment which entered employment provides it, by position
 * @param provider who provides it and on what footing
 * @param occupied the period the employee occupied it within the tax year
 * @param band the city's population band, for owned accommodation
 * @param employerCost the licence fee, lease rental or hotel charges for the period, for all but owned
 * @param rentPaid the rent the employee actually paid for the period
 * @param furniture the furniture, where the accommodation is furnished
 * @param firstProvidedIn the tax year the accommodation was first provided to the employee
 */
public record EmployerAccommodation(
        int employment,
        AccommodationProvider provider,
        DateRange occupied,
        Optional<PopulationBand> band,
        Optional<Money> employerCost,
        Money rentPaid,
        Optional<Furniture> furniture,
        TaxYearId firstProvidedIn) {

    public EmployerAccommodation {
        if (provider == null || occupied == null || band == null || employerCost == null || rentPaid == null
                || furniture == null || firstProvidedIn == null) {
            throw new IllegalArgumentException("Employer accommodation needs its provider, period, rent paid and"
                    + " first year; pass Optional.empty() for a fact not given");
        }
        if (employment < 0) {
            throw new IllegalArgumentException("An employment position may not be negative, but was " + employment);
        }
        requireTheFactsTheFootingReads(provider, band, employerCost, furniture);
        if (rentPaid.isLessThan(Money.ZERO)
                || employerCost.filter(amount -> amount.isLessThan(Money.ZERO)).isPresent()) {
            throw new IllegalArgumentException("Accommodation amounts may not be negative");
        }
    }

    /** A fact the footing reads must be given, and one it does not read is refused rather than ignored. */
    private static void requireTheFactsTheFootingReads(AccommodationProvider provider, Optional<PopulationBand> band,
            Optional<Money> employerCost, Optional<Furniture> furniture) {
        boolean owned = provider == AccommodationProvider.EMPLOYER_OWNED;
        if (owned != band.isPresent()) {
            throw new IllegalArgumentException(owned
                    ? "Owned accommodation is valued by the city's 2011-census population; state the band"
                    : provider + " is not valued by population; the band would be ignored, so it is refused");
        }
        if (owned == employerCost.isPresent()) {
            throw new IllegalArgumentException(owned
                    ? "Owned accommodation has no employer's cost to enter; it would be ignored"
                    : provider + " is valued on the employer's cost for the period; enter it");
        }
        if (provider == AccommodationProvider.HOTEL && furniture.isPresent()) {
            throw new IllegalArgumentException("A hotel is valued on its charges; furniture is not applicable");
        }
    }
}
