package com.thiru.wealthlens.taxengine.rules.definition;

/**
 * The computation a row of {@code RS-PERQ-CAR-PATH} puts a vehicle on (W-154e2, D-330): which Table II arithmetic
 * applies is the law's, so each row states the vehicle facts it covers as conditions and the computation here, and a
 * changed condition is new data rather than new Java — as {@link BucketPart} does for capital gains (D-289).
 *
 * @param computation the arithmetic the row's vehicles are valued by
 */
public record VehiclePath(VehicleComputation computation) implements RuleDefinition {

    public VehiclePath {
        if (computation == null) {
            throw new IllegalArgumentException("A vehicle-path row must name its computation");
        }
    }
}
