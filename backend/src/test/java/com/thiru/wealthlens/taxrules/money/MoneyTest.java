package com.thiru.wealthlens.taxrules.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Money is asserted with AssertJ's {@code isEqualTo} rather than {@code MoneyAssert} (decision
 * D-010). {@code MoneyAssert} is {@code double}-based with a half-paisa tolerance, which exists to
 * stop a bare {@code assertEquals(double, double)}. That hazard does not arise for an exact type,
 * and routing through it would discard the exactness this type exists to provide.
 *
 * <p>Record equality compares {@code BigDecimal} scale as well as value, so {@code 1230} and
 * {@code 1230.00} are unequal. That is deliberate: it catches a scale slip as well as a value slip.
 */
class MoneyTest {

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        void of_whenGivenMoreThanTwoDecimals_roundsToPaise() {
            // Given / When
            Money money = Money.of("145.375");

            // Then — scale 2, HALF_UP
            assertThat(money.amountInRupees()).isEqualTo(new BigDecimal("145.38"));
        }

        @Test
        void of_whenGivenWholeRupees_carriesPaiseScale() {
            assertThat(Money.of("1230").amountInRupees()).isEqualTo(new BigDecimal("1230.00"));
        }

        @Test
        void construction_whenAmountIsNull_isRejected() {
            assertThatThrownBy(() -> new Money(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("may not be null");
        }
    }

    @Nested
    @DisplayName("arithmetic")
    class Arithmetic {

        @Test
        void times_whenWholeMultiple_isExact() {
            // ₹3,000 per month for 24 child-months — PerUnitPerPeriod's annual equivalent
            assertThat(Money.of("3000").times(24)).isEqualTo(Money.of("72000"));
        }

        @Test
        void times_byZero_isZero() {
            assertThat(Money.of("3000").times(0)).isEqualTo(Money.ZERO);
        }

        @Test
        void times_preservesPaise() {
            assertThat(Money.of("0.05").times(3)).isEqualTo(Money.of("0.15"));
        }

        @Test
        void times_byANegative_negates() {
            assertThat(Money.of("3000").times(-2)).isEqualTo(Money.of("-6000"));
        }

        @Test
        void plus_whenAddingPaise_isExact() {
            assertThat(Money.of("0.10").plus(Money.of("0.20"))).isEqualTo(Money.of("0.30"));
        }

        @Test
        void minus_whenResultIsNegative_keepsTheSign() {
            assertThat(Money.of("100").minus(Money.of("150"))).isEqualTo(Money.of("-50"));
        }

        @Test
        void floorAtZero_whenNegative_becomesZero() {
            assertThat(Money.of("-1").floorAtZero()).isEqualTo(Money.ZERO);
        }

        @Test
        void floorAtZero_whenZero_isUnchanged() {
            assertThat(Money.ZERO.floorAtZero()).isEqualTo(Money.ZERO);
        }

        @Test
        void floorAtZero_whenPositive_isUnchanged() {
            assertThat(Money.of("1").floorAtZero()).isEqualTo(Money.of("1"));
        }

        @Test
        void minAndMax_whenGivenTwoAmounts_pickCorrectly() {
            Money small = Money.of("10");
            Money large = Money.of("20");
            assertThat(Money.min(small, large)).isEqualTo(small);
            assertThat(Money.max(small, large)).isEqualTo(large);
        }

        @Test
        void comparisons_whenAmountsDiffer_areOrdered() {
            Money ten = Money.of("10");
            Money twenty = Money.of("20");
            assertThat(ten.isLessThan(twenty)).isTrue();
            assertThat(ten.isLessThanOrEqual(ten)).isTrue();
            assertThat(twenty.isGreaterThan(ten)).isTrue();
        }
    }

    /**
     * Section 516 of the Income-tax Act 2025, as recorded at rule {@code RATE-ROUND-002}: an amount
     * is <i>"rounded off to the nearest multiple of ₹10, ignoring any part of a rupee consisting of
     * paise"</i>, increased if the last figure is five or more and reduced if it is less.
     *
     * <p>The two steps happen in that order, and the order is the whole subtlety.
     */
    @Nested
    @DisplayName("section 516 rounding")
    class Section516Rounding {

        @Test
        void roundToNearestTen_whenLastFigureIsBelowFive_roundsDown() {
            // RATE-ROUND-002: last figure 4, so reduced to the next lower multiple of ten.
            assertThat(Money.of("1234").roundToNearestTenPerSection516()).isEqualTo(Money.of("1230"));
        }

        @Test
        void roundToNearestTen_whenLastFigureIsExactlyFive_roundsUp() {
            // RATE-ROUND-002: "five or more" is increased, so 5 goes up, not to the even neighbour.
            assertThat(Money.of("1235").roundToNearestTenPerSection516()).isEqualTo(Money.of("1240"));
        }

        @Test
        void roundToNearestTen_whenLastFigureIsNine_roundsUp() {
            assertThat(Money.of("1239").roundToNearestTenPerSection516()).isEqualTo(Money.of("1240"));
        }

        @Test
        void roundToNearestTen_whenAlreadyAMultipleOfTen_isUnchanged() {
            assertThat(Money.of("1230").roundToNearestTenPerSection516()).isEqualTo(Money.of("1230"));
        }

        /**
         * The case a plausible implementation gets wrong: 1,234.99 is <b>not</b> 1,235 rounding up.
         * The paise are discarded first, leaving 1,234, whose last figure is 4.
         *
         * <p>Source: {@code 13-edge-cases.md} T13-14 — <i>"Refund of ₹1,234.99 — paise ignored →
         * 1,234 → last figure 4 → ₹1,230."</i>
         */
        @Test
        void roundToNearestTen_whenPaiseWouldCarry_discardsThemFirst() {
            assertThat(Money.of("1234.99").roundToNearestTenPerSection516()).isEqualTo(Money.of("1230"));
        }

        @Test
        void roundToNearestTen_whenZero_isZero() {
            assertThat(Money.ZERO.roundToNearestTenPerSection516()).isEqualTo(Money.ZERO);
        }

        /**
         * Negative amounts arise as refunds and as losses. Rule ROUND-NEG-001 is STATUS UNCLEAR —
         * section 516 is drafted for a positive amount and "increased to the next higher" is
         * ambiguous for a refund. These assertions pin the stated reading: round the magnitude,
         * restore the sign. If CBDT guidance settles it the other way, these are the tests to change,
         * and the rule row is where the change is justified.
         */
        @Test
        void roundToNearestTen_whenNegative_roundsTheMagnitudeAndRestoresTheSign() {
            assertThat(Money.of("-1234").roundToNearestTenPerSection516()).isEqualTo(Money.of("-1230"));
            assertThat(Money.of("-1235").roundToNearestTenPerSection516()).isEqualTo(Money.of("-1240"));
            assertThat(Money.of("-1234.99").roundToNearestTenPerSection516()).isEqualTo(Money.of("-1230"));
        }
    }
}
