package com.thiru.wealthlens.taxengine.input;

import java.util.Optional;

/**
 * A car's engine, as the taxpayer knows it: whether it is electric, and otherwise its cubic capacity (W-088n1, D-257).
 *
 * <p>Facts, not Table II's columns. Which column a car falls in — "does not exceed 1.6 litres", or an electric vehicle
 * where the year's Table names one — is the rows' to say (S4 r.15(3); Rules 1962 r.3(2); D-113), so a Table that moves
 * the line or adds a column is new rule data, not a new value here.
 *
 * @param electric whether the car is an electric vehicle
 * @param cubicCapacityCc the engine's cubic capacity in cc; present exactly when the car is not electric, which has
 *     none
 */
public record CarEngine(boolean electric, Optional<Integer> cubicCapacityCc) {

    public CarEngine {
        if (cubicCapacityCc == null) {
            throw new IllegalArgumentException("A car engine's capacity is Optional.empty() when it has none");
        }
        if (electric == cubicCapacityCc.isPresent()) {
            throw new IllegalArgumentException(electric
                    ? "An electric car has no cubic capacity; enter none"
                    : "A car that is not electric is valued by its cubic capacity; state it in cc");
        }
        if (cubicCapacityCc.isPresent() && cubicCapacityCc.get() <= 0) {
            throw new IllegalArgumentException("A cubic capacity is a positive number of cc, but was "
                    + cubicCapacityCc.get());
        }
    }

    /** An engine of the given cubic capacity, in cc. */
    public static CarEngine ofCapacity(int cubicCapacityCc) {
        return new CarEngine(false, Optional.of(cubicCapacityCc));
    }

    /** An electric vehicle's. */
    public static CarEngine electricVehicle() {
        return new CarEngine(true, Optional.empty());
    }
}
