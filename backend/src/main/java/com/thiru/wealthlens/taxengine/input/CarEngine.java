package com.thiru.wealthlens.taxengine.input;

/**
 * A car's engine, which chooses Table II's column (S4 r.15(3); Rules 1962 r.3(2); D-113).
 *
 * <p>Electric is its own value because the two years differ: TY 2026-27's lower column reads "does not exceed 1.6
 * litres or the motor car is an electric vehicle"; TY 2025-26's has no such words, and an electric car has no cubic
 * capacity, so it is refused there (D-113 C5a).
 */
public enum CarEngine {

    /** Cubic capacity not over 1.6 litres. */
    UP_TO_1600_CC,

    /** Cubic capacity over 1.6 litres. */
    ABOVE_1600_CC,

    /** An electric vehicle. */
    ELECTRIC
}
