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
 *
 * <p><b>Since TL-8, prefer {@link #assertCanonical}.</b> A <em>stored</em> amount now goes through
 * {@code TMoney}, so it should equal its own two-decimal rounding exactly — and asserting that
 * catches an uncanonicalised write, which {@link #assertMoney}'s half-paisa tolerance hides by
 * design. Keep {@code assertMoney} for intermediate values and for anything compared against a
 * figure computed a different way; use {@code assertCanonical} for anything that reaches a
 * document.
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
     * Asserts a stored amount is exactly the value it claims to be, to the paisa.
     *
     * <p>Exact equality, deliberately. Every amount written to a document is canonicalised through
     * {@code TMoney} (TL-8), so an amount that fails this has skipped that — which is the defect
     * worth catching, and precisely what a tolerance would swallow.
     */
    public static void assertCanonical(double expected, double actual) {
        assertThat(actual).isEqualTo(expected);
    }

    /** As {@link #assertCanonical(double, double)}, describing the amount on failure. */
    public static void assertCanonical(String description, double expected, double actual) {
        assertThat(actual).as(description).isEqualTo(expected);
    }

    /**
     * Asserts an amount is canonical to paise — that it equals its own two-decimal rounding.
     *
     * <p>For a value whose exact figure the test does not want to restate, where the property under
     * test is only that it went through {@code TMoney} on the way to storage.
     */
    public static void assertCanonicalToPaise(String description, double actual) {
        assertThat(actual)
                .as("%s is not canonical to paise: %s", description, actual)
                .isEqualTo(java.math.BigDecimal.valueOf(actual)
                        .setScale(2, java.math.RoundingMode.HALF_UP).doubleValue());
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
