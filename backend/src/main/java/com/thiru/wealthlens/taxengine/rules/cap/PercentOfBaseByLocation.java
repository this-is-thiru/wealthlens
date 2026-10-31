package com.thiru.wealthlens.taxengine.rules.cap;

import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * A percentage of a base, the percentage chosen by location (D-099).
 *
 * <p>HRA's third limb: "such percentage of salary" as the table gives for the location (S4 r.279(1)(c) Table;
 * Rules 1962 r.2A(c)). The list of places is law and changes by year — four cities in r.2A, eight in r.279 — so
 * it is data here. A location must be listed or be {@link #ANY_OTHER_PLACE}; anything else is refused, because a
 * misspelt city falling quietly into the lower percentage is a plausible, wrong, untraceable figure.
 *
 * @param base the amount the percentage is of, such as salary for the period
 * @param location which of the context's locations to read
 * @param bands the listed places, each with its percentage; no place in two bands
 * @param otherwise the percentage for {@link #ANY_OTHER_PLACE}
 */
public record PercentOfBaseByLocation(BaseId base, BaseId location, List<Band> bands, Rate otherwise)
        implements CapShape {

    /** The location code for every place no band lists — the table's "any other place". */
    public static final String ANY_OTHER_PLACE = "ANY_OTHER_PLACE";

    /**
     * Places sharing one percentage.
     *
     * @param locations location codes
     * @param percent their percentage of the base
     */
    public record Band(Set<String> locations, Rate percent) {

        public Band {
            if (locations == null || locations.isEmpty() || percent == null) {
                throw new IllegalArgumentException("A location band needs at least one place and a percentage");
            }
            if (locations.contains(ANY_OTHER_PLACE)) {
                throw new IllegalArgumentException(ANY_OTHER_PLACE + " is the otherwise percentage, not a band's");
            }
            locations = Set.copyOf(locations);
        }
    }

    public PercentOfBaseByLocation {
        if (base == null || location == null || bands == null || bands.isEmpty() || otherwise == null) {
            throw new IllegalArgumentException(
                    "A PercentOfBaseByLocation needs a base, a location, at least one band and an otherwise rate");
        }
        bands = List.copyOf(bands);
        Set<String> seen = new HashSet<>();
        for (Band band : bands) {
            for (String place : band.locations()) {
                if (!seen.add(place)) {
                    throw new IllegalArgumentException(place + " is in two bands; its percentage would be ambiguous");
                }
            }
        }
    }

    @Override
    public CapResult apply(CapContext ctx) {
        String place = ctx.location(location);
        Rate percent = percentFor(place);
        return new CapResult(percent.of(ctx.base(base)), percent + " of " + base + " at " + place);
    }

    private Rate percentFor(String place) {
        if (ANY_OTHER_PLACE.equals(place)) {
            return otherwise;
        }
        return bands.stream()
                .filter(band -> band.locations().contains(place))
                .map(Band::percent)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Location " + place + " is not in this year's table "
                        + known() + "; enter a listed place or " + ANY_OTHER_PLACE));
    }

    private Set<String> known() {
        Set<String> known = new TreeSet<>();
        bands.forEach(band -> known.addAll(band.locations()));
        known.add(ANY_OTHER_PLACE);
        return known;
    }

    @Override
    public String shapeName() {
        return "percentage of " + base + " by " + location;
    }
}
