package com.thiru.wealthlens.taxplanning.portfolio;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.math.BigDecimal;

/**
 * The only place the two money representations meet ({@code 19-engineering-foundations.md} E1; D-240).
 *
 * <p>{@code portfolio} speaks canonical {@code double} rupees (TL-8's {@code TMoney}); the engine speaks exact decimal
 * {@link Money}. The widening is lossless only because the double is already canonical to paise — so that is checked
 * here, not assumed: {@code Money} would round a stray {@code 145.37999999999997} to 145.38 without a word, and the
 * seam that claims to lose nothing would then be the place a figure silently changed. Nothing flows back.
 */
public final class MoneyBridge {

    private static final int PAISE_PLACES = 2;

    private MoneyBridge() {
    }

    /**
     * A canonical rupee amount as exact {@link Money}.
     *
     * @throws IllegalArgumentException if the amount is not finite, or not canonical to paise
     */
    public static Money fromTMoney(double canonicalRupees) {
        if (Double.isNaN(canonicalRupees) || Double.isInfinite(canonicalRupees)) {
            throw new IllegalArgumentException("A portfolio amount was " + canonicalRupees + "; it cannot become a tax"
                    + " figure");
        }
        BigDecimal exact = BigDecimal.valueOf(canonicalRupees);
        if (exact.stripTrailingZeros().scale() > PAISE_PLACES) {
            throw new IllegalArgumentException("A portfolio amount of " + canonicalRupees + " is not canonical to paise;"
                    + " every stored amount goes through TMoney (TL-8), so this one skipped it upstream");
        }
        return Money.ofExact(exact);
    }

    /** Refused deliberately: a tax figure is exact by construction and must not re-enter a {@code double} world. */
    public static double toTMoney(Money amount) {
        throw new UnsupportedOperationException("A tax figure is exact by construction (TL-8 §7). Converting it back"
                + " to double would discard that guarantee at the one point it matters — the figure the user files on");
    }
}
