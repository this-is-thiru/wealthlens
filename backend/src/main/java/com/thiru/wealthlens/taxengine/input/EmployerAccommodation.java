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
 * population for owned accommodation (the Rules name no cities; the bands are the row's, D-258), the employer's cost for the period — the
 * licence fee, the lease rental or the hotel charges — the rent the employee paid, and the furniture. The first tax
 * year it was provided decides whether the Cost Inflation Index cap applies (r.15(2)(d); r.3(1) third proviso).
 * Which provider is measured on which of these is its Table I rows' to say; the computation refuses a fact no row
 * reads (W-158b).
 *
 * @param employment which entered employment provides it, by position
 * @param provider who provides it and on what footing
 * @param occupied the period the employee occupied it within the tax year
 * @param cityPopulation the city's population as the Table's census counts it — the 2011 census in both years' Tables,
 *     not today's — for owned accommodation
 * @param employerCost the licence fee, lease rental or hotel charges for the period, for all but owned
 * @param rentPaid the rent the employee actually paid for the period
 * @param furniture the furniture, where the accommodation is furnished
 * @param firstProvidedIn the tax year the accommodation was first provided to the employee
 * @param firstYearValue for owned or leased accommodation continued from an earlier year, the amount the Table
 *     gave for the first tax year, after rent, as the employer calculated it — the figure the Cost Inflation Index
 *     cap scales (D-115)
 */
public record EmployerAccommodation(
        int employment,
        AccommodationProvider provider,
        DateRange occupied,
        Optional<Integer> cityPopulation,
        Optional<Money> employerCost,
        Money rentPaid,
        Optional<Furniture> furniture,
        TaxYearId firstProvidedIn,
        Optional<Money> firstYearValue) {

    public EmployerAccommodation {
        if (provider == null || occupied == null || cityPopulation == null || employerCost == null || rentPaid == null
                || furniture == null || firstProvidedIn == null || firstYearValue == null) {
            throw new IllegalArgumentException("Employer accommodation needs its provider, period, rent paid and"
                    + " first year; pass Optional.empty() for a fact not given");
        }
        if (employment < 0) {
            throw new IllegalArgumentException("An employment position may not be negative, but was " + employment);
        }
        requireSigns(cityPopulation, employerCost, rentPaid, firstYearValue);
    }

    /** A population counts people, so is positive; an amount may not be negative. */
    private static void requireSigns(Optional<Integer> cityPopulation, Optional<Money> employerCost, Money rentPaid,
            Optional<Money> firstYearValue) {
        if (cityPopulation.filter(people -> people <= 0).isPresent()) {
            throw new IllegalArgumentException("A city's population is a positive number of people, but was "
                    + cityPopulation.get());
        }
        if (rentPaid.isLessThan(Money.ZERO)
                || employerCost.filter(amount -> amount.isLessThan(Money.ZERO)).isPresent()
                || firstYearValue.filter(amount -> amount.isLessThan(Money.ZERO)).isPresent()) {
            throw new IllegalArgumentException("Accommodation amounts may not be negative");
        }
    }
}
