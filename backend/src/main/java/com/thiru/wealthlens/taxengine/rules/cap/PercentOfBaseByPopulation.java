package com.thiru.wealthlens.taxengine.rules.cap;

import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A percentage of a base, the percentage chosen by the population of the place (W-088n2, D-258).
 *
 * <p>Owned accommodation's Table I Sl. 2(a): 10% "in cities having population exceeding forty lakhs", 7.5% "exceeding
 * fifteen lakhs but not exceeding forty lakhs", 5% "in other areas" (S4 r.15(2); Rules 1962 r.3(1)). The thresholds
 * are law and data here; the population is the taxpayer's fact. "Exceeding" is strict, so a city of exactly forty
 * lakh is in the lower band.
 *
 * @param base the amount the percentage is of, such as salary for the period
 * @param population which of the context's counts is the population
 * @param bands each threshold the population must exceed, with its percentage; no threshold twice
 * @param otherwise the percentage where the population exceeds no band's threshold
 */
public record PercentOfBaseByPopulation(BaseId base, BaseId population, List<Band> bands, Rate otherwise)
        implements CapShape {

    /**
     * Places whose population exceeds a threshold.
     *
     * @param exceeding the threshold, which the population must be strictly greater than
     * @param percent their percentage of the base
     */
    public record Band(int exceeding, Rate percent) {

        public Band {
            if (exceeding < 0 || percent == null) {
                throw new IllegalArgumentException("A population band needs a threshold of zero or more and a"
                        + " percentage, but had " + exceeding);
            }
        }
    }

    public PercentOfBaseByPopulation {
        if (base == null || population == null || bands == null || bands.isEmpty() || otherwise == null) {
            throw new IllegalArgumentException("A PercentOfBaseByPopulation needs a base, a population, at least one"
                    + " band and an otherwise rate");
        }
        Set<Integer> seen = new HashSet<>();
        for (Band band : bands) {
            if (!seen.add(band.exceeding())) {
                throw new IllegalArgumentException("Two population bands share the threshold " + band.exceeding()
                        + "; the percentage there would be ambiguous");
            }
        }
        bands = bands.stream().sorted(Comparator.comparingInt(Band::exceeding).reversed()).toList();
    }

    @Override
    public CapResult apply(CapContext ctx) {
        int people = ctx.count(population);
        if (people < 0) {
            throw new IllegalArgumentException("A population may not be negative, but was " + people);
        }
        Optional<Band> exceeded = bands.stream().filter(band -> people > band.exceeding()).findFirst();
        Rate percent = exceeded.map(Band::percent).orElse(otherwise);
        return new CapResult(percent.of(ctx.base(base)), percent + " of " + base + " for a population of " + people
                + exceeded.map(band -> ", exceeding " + band.exceeding()).orElse(", exceeding no band"));
    }

    @Override
    public String shapeName() {
        return "percentage of " + base + " by " + population;
    }
}
