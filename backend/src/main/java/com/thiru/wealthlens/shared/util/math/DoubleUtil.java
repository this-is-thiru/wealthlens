package com.thiru.wealthlens.shared.util.math;

import lombok.experimental.UtilityClass;

@UtilityClass
public class DoubleUtil {
    private static final double EPSILON = 1e-9;

    public static boolean equal(double a, double b) {
        return Math.abs(a - b) < EPSILON;
    }
}
