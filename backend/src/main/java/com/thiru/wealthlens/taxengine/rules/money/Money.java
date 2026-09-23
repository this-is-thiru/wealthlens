package com.thiru.wealthlens.taxengine.rules.money;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * An exact rupee amount.
 *
 * <p>The rest of this codebase holds money as a canonicalised {@code double} through
 * {@code TMoney}, and that decision (TL-8) is correct for a personal tracker. This module is the
 * case TL-8 section 7 names as its own trigger: <i>"a regulator or auditor requires exact decimal
 * storage &mdash; for a personal tracker this is remote; for anything filed on someone else's behalf
 * it is not."</i> This module produces figures a person files on.
 *
 * <p>The migration cost that made {@code BigDecimal} premature for the ledger &mdash; some 170
 * declarations and every stored document &mdash; does not exist here: this is a new module with no
 * data. The two representations meet at exactly one seam, {@code MoneyBridge}, and only inbound.
 *
 * <p>This is one of two packages permitted to call {@link BigDecimal#setScale(int, RoundingMode)};
 * see {@code ArchitectureTest.money_rounding_lives_in_one_place}.
 */
public record Money(BigDecimal amountInRupees) implements Comparable<Money> {

    /** Rupees and paise. The smallest unit that exists. */
    public static final int SCALE = 2;

    /**
     * Twelve digits for intermediates, so a percentage of salary is not rounded until it becomes a
     * {@code Money}. Rounding at every step and rounding once produce different answers, and only
     * one of them can be derived by hand from the rulebook.
     */
    public static final MathContext CALC = new MathContext(12, RoundingMode.HALF_UP);

    public static final Money ZERO = of("0");
    public static final Money TEN = of("10");

    public Money {
        if (amountInRupees == null) {
            throw new IllegalArgumentException("A money amount may not be null");
        }
        amountInRupees = amountInRupees.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static Money of(String rupees) {
        return new Money(new BigDecimal(rupees));
    }

    public static Money ofExact(BigDecimal raw) {
        return new Money(raw);
    }

    public Money plus(Money other) {
        return ofExact(amountInRupees.add(other.amountInRupees));
    }

    public Money minus(Money other) {
        return ofExact(amountInRupees.subtract(other.amountInRupees));
    }

    /**
     * An exact whole multiple — {@code ₹3,000 × 24 months} and the like.
     *
     * <p>Exact by construction, so unlike {@link Rate#of} there is nothing to round: an integer
     * multiple of a two-decimal amount is a two-decimal amount. It takes an {@code int} rather than
     * a {@code Money} or a {@code BigDecimal} precisely so that it cannot be used to scale by a
     * fraction, which is what {@link Rate} is for.
     *
     * @param multiplier a count — children, months, journeys — never a rate
     */
    public Money times(int multiplier) {
        return ofExact(amountInRupees.multiply(BigDecimal.valueOf(multiplier)));
    }

    /** Clamps a negative amount to zero. Named for what it does, not for what it guards against. */
    public Money floorAtZero() {
        return amountInRupees.signum() < 0 ? ZERO : this;
    }

    public boolean isLessThan(Money other) {
        return compareTo(other) < 0;
    }

    public boolean isLessThanOrEqual(Money other) {
        return compareTo(other) <= 0;
    }

    public boolean isGreaterThan(Money other) {
        return compareTo(other) > 0;
    }

    public static Money min(Money left, Money right) {
        return left.isLessThan(right) ? left : right;
    }

    public static Money max(Money left, Money right) {
        return left.isLessThan(right) ? right : left;
    }

    /**
     * Rounds to the nearest ten rupees, as section 516 of the Income-tax Act 2025 requires of a
     * total income and of an amount payable or refundable.
     *
     * <p>The section is drafted in two steps and the order matters: <i>ignore any part of a rupee
     * consisting of paise</i>, and only then round the whole rupees &mdash; up if the last figure is
     * five or more, down if less. So 1,234.99 rounds to 1,230, not to 1,240.
     *
     * <p>Applied to the magnitude with the sign restored. The section is written for a positive
     * amount, and "increased to the next higher" is ambiguous for a refund; rule ROUND-NEG-001 is
     * recorded as STATUS UNCLEAR and this is the reading it states.
     */
    public Money roundToNearestTenPerSection516() {
        BigDecimal wholeRupees = amountInRupees.abs().setScale(0, RoundingMode.DOWN);
        BigDecimal lastFigure = wholeRupees.remainder(BigDecimal.TEN);
        BigDecimal rounded = lastFigure.compareTo(BigDecimal.valueOf(5)) >= 0
                ? wholeRupees.subtract(lastFigure).add(BigDecimal.TEN)
                : wholeRupees.subtract(lastFigure);
        return new Money(amountInRupees.signum() < 0 ? rounded.negate() : rounded);
    }

    @Override
    public int compareTo(Money other) {
        return amountInRupees.compareTo(other.amountInRupees);
    }

    @Override
    public String toString() {
        return amountInRupees.toPlainString();
    }
}
