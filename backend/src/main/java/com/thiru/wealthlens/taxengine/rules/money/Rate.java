package com.thiru.wealthlens.taxengine.rules.money;

import java.math.BigDecimal;

/**
 * A statutory rate, held as a fraction.
 *
 * <p>This type exists so that {@code 30} and {@code 0.30} stop being the same type. A percentage
 * stored one way in one rule pack and the other way in another is a mistake the compiler cannot see
 * and a reviewer will not notice &mdash; and it is invisible until a tax bill is a hundred times
 * too large, or a hundredth.
 *
 * <p>A rate is always constructed from the percentage as the statute states it:
 * {@code Rate.percent("30")} for a thirty per cent slab, {@code Rate.percent("4")} for the health
 * and education cess. The division to a fraction happens once, here.
 *
 * <p>{@link #of(Money)} is the only way to apply one. It multiplies at {@link Money#CALC}'s twelve
 * digits and rounds to paise once, at the boundary &mdash; so a percentage of a salary is not
 * rounded until it becomes an amount. Rounding at every step and rounding once give different
 * answers, and only one of them can be derived by hand from the rulebook.
 */
public record Rate(BigDecimal fraction) implements Comparable<Rate> {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public static final Rate ZERO = percent("0");

    public Rate {
        if (fraction == null) {
            throw new IllegalArgumentException("A rate may not be null");
        }
    }

    /**
     * Builds a rate from the percentage as the statute states it.
     *
     * @param percentage e.g. {@code "30"}, {@code "12.5"}, {@code "4"} — never {@code "0.30"}
     */
    public static Rate percent(String percentage) {
        return new Rate(new BigDecimal(percentage).divide(HUNDRED, Money.CALC));
    }

    /** Applies the rate, rounding to paise once. */
    public Money of(Money base) {
        return Money.ofExact(base.amountInRupees().multiply(fraction, Money.CALC));
    }

    /**
     * An annual rate applied to a base for {@code part} of {@code whole} periods — a month's interest on a month-end
     * balance (D-118). Multiplied and divided at {@link Money#CALC} and rounded to paise once, so a year of months
     * adds up without twelve roundings.
     *
     * @param part the periods, 0 or more
     * @param whole the periods in the year, more than 0
     */
    public Money ofShare(Money base, int part, int whole) {
        if (whole <= 0 || part < 0) {
            throw new IllegalArgumentException("A share needs part >= 0 and whole > 0; got " + part + " of " + whole);
        }
        return Money.ofExact(base.amountInRupees().multiply(fraction, Money.CALC).multiply(BigDecimal.valueOf(part))
                .divide(BigDecimal.valueOf(whole), Money.CALC));
    }

    /** The rate as the statute writes it, for a trace line: {@code "30%"}, {@code "12.5%"}. */
    public String asPercentLabel() {
        return fraction.multiply(HUNDRED).stripTrailingZeros().toPlainString() + "%";
    }

    @Override
    public int compareTo(Rate other) {
        return fraction.compareTo(other.fraction);
    }

    @Override
    public String toString() {
        return asPercentLabel();
    }
}
