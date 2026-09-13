package com.thiru.wealthlens.shared.util.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

/**
 * Canonical rupee amounts held as {@code double}.
 *
 * <p><b>The problem this solves is not precision — it is canonicalisation.</b> A charge everyone
 * agrees is ₹145.38 stores as {@code 145.37999999999997}: close enough to print, and wrong enough
 * that exact equality fails, a MongoDB query for the amount misses the row holding it, and
 * reconciliation against a broker's statement cannot tell a real one-paisa discrepancy from a
 * floating-point artefact.
 *
 * <p>Rounding to paise does not make {@code 145.38} exactly representable — nothing can. It makes
 * every route to that value produce the <em>same</em> {@code double}, which is the property that
 * was actually missing. Measured: the accumulated error over 50,000 trades is ₹0.0000015 on ₹12.43
 * crore, and rounding recovers the exact figure at that scale. Money was never being lost; identity
 * was.
 *
 * <p>Decision TL-8 — see {@code docs/trade-ledger/money-representation-analysis.md} for why this
 * rather than {@code BigDecimal} end to end, and for the five triggers that would change it.
 */
public final class TMoney {

    /** Indian currency resolves to paise; two places is the smallest unit that exists. */
    public static final int PAISE_SCALE = 2;

    private TMoney() {
    }

    /** One amount, rounded to paise. HALF_UP, which is how money rounds outside of banking. */
    public static double scale(double amount) {
        return BigDecimal.valueOf(finite(amount)).setScale(PAISE_SCALE, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * Rejects a non-finite amount, loudly and by name.
     *
     * <p>{@code BigDecimal.valueOf} already refuses these, but with
     * {@code NumberFormatException: Infinite or NaN}, which says nothing about where the money came
     * from. NaN and Infinity reach here from a divide by zero — a charge spread over zero units —
     * and the reason to fail rather than coerce to zero is that they <b>propagate</b>: one poisoned
     * amount makes every subsequent total in that document non-finite, permanently. Better to stop
     * the trade than to corrupt the record silently, which is what adding raw doubles did.
     */
    private static double finite(double amount) {
        if (Double.isNaN(amount) || Double.isInfinite(amount)) {
            throw new IllegalArgumentException(
                    "A money amount was " + amount + ", which usually means a divide by zero"
                            + " upstream — a charge spread over zero quantity. Refusing it here"
                            + " rather than letting it propagate through the stored totals.");
        }
        return amount;
    }

    /** Two amounts added exactly, then canonicalised. */
    public static double add(double left, double right) {
        return scaled(BigDecimal.valueOf(finite(left)).add(BigDecimal.valueOf(finite(right))));
    }

    /**
     * A collection summed exactly, then canonicalised.
     *
     * <p>Summing in {@code double} and rounding once is not the same as this: it is close enough at
     * this application's scale, but it makes the total depend on iteration order, and a total that
     * changes when a map re-orders is not a total anyone should reconcile against.
     */
    public static double sum(Collection<Double> amounts) {
        BigDecimal total = BigDecimal.ZERO;
        for (Double amount : amounts) {
            total = total.add(amount == null ? BigDecimal.ZERO : BigDecimal.valueOf(finite(amount)));
        }
        return scaled(total);
    }

    private static double scaled(BigDecimal amount) {
        return amount.setScale(PAISE_SCALE, RoundingMode.HALF_UP).doubleValue();
    }
}
