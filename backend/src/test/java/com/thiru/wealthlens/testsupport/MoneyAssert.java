package com.thiru.wealthlens.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Map;

/**
 * Assertions for monetary values.
 *
 * <p>Never assert money with a bare {@code assertEquals(double, double)} — that is an exact bit
 * comparison, so reordering two arithmetic operations turns a correct result into
 * {@code expected 57.55 but was 57.550000000000004}. Every money assertion goes through this class.
 */
public final class MoneyAssert {

    /** Half a paisa. Two values closer than this represent the same rupee amount. */
    public static final double PAISA = 0.005;

    private MoneyAssert() {
    }

    /** Asserts two rupee amounts are equal to within half a paisa. */
    public static void assertMoney(double expected, double actual) {
        assertThat(actual).isCloseTo(expected, within(PAISA));
    }

    /** Asserts two rupee amounts are equal, describing the amount on failure. */
    public static void assertMoney(String description, double expected, double actual) {
        assertThat(actual).as(description).isCloseTo(expected, within(PAISA));
    }

    /** Asserts an amount is exactly zero, allowing for negative-zero and accumulated drift. */
    public static void assertNoCharge(double actual) {
        assertThat(actual).isCloseTo(0.0, within(PAISA));
    }

    /**
     * Asserts every entry of a charge breakdown, keyed by charge code. Reports every mismatch in
     * one failure rather than stopping at the first, and fails if either map has keys the other
     * lacks — a missing charge line is as much a defect as a wrong amount.
     */
    public static void assertBreakdown(Map<String, Double> expected, Map<String, Double> actual) {
        assertThat(actual.keySet())
                .as("charge codes present in the breakdown")
                .containsExactlyInAnyOrderElementsOf(expected.keySet());

        assertThat(actual)
                .allSatisfy((code, amount) ->
                        assertThat(amount).as("charge %s", code).isCloseTo(expected.get(code), within(PAISA)));
    }
}
