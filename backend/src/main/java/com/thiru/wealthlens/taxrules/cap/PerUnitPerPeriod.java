package com.thiru.wealthlens.taxrules.cap;

import com.thiru.wealthlens.taxrules.id.BaseId;
import com.thiru.wealthlens.taxrules.money.Money;
import java.util.Optional;

/**
 * An amount per unit per period — &#8377;3,000 a month per child, capped at two children.
 *
 * <h2>It computes its own annual equivalent</h2>
 *
 * <p>The whole reason this is a shape rather than a pair of numbers on a rule row. The placeholder
 * carried {@code annualLimitFixed} and {@code monthlyLimitFixed} side by side and divided one into
 * the other, which is where observation O-10's divide-by-zero came from. Here there is one figure,
 * stated as the Rules state it, and the annual equivalent is a <b>multiplication</b>. Nothing
 * divides, so nothing can divide by zero ({@code D-CAPTYPE}).
 *
 * <h2>Fractions cannot reach this shape</h2>
 *
 * <p>{@code 13-edge-cases.md} B2-20 leaves an open question: a child in hostel for twenty days is
 * one month or zero, <em>not</em> 0.65. That decision belongs upstream, to whichever proration basis
 * the rule names — completed months, calendar months, days — and it arrives here already resolved.
 * The counts are {@code int}, which is the enforcement rather than a convention: this shape has no
 * way to express a part period and so cannot quietly decide the question.
 *
 * @param amount the figure as the Rules state it, per unit per period
 * @param unit what there may be several of — children, typically. Empty where the limit has no
 *     per-unit dimension at all, as in {@code DED-80GG-001} limb (b), &#8377;5,000 per month
 * @param maxUnits the statutory maximum number of units; must be 1 where there is no unit
 * @param period what the amount is stated per — months, for every rule in the rulebook today
 */
public record PerUnitPerPeriod(Money amount, Optional<BaseId> unit, int maxUnits, BaseId period)
        implements CapShape {

    public PerUnitPerPeriod {
        if (amount == null || unit == null || period == null) {
            throw new IllegalArgumentException("A PerUnitPerPeriod's amount, unit and period may not be null");
        }
        if (maxUnits < 1) {
            throw new IllegalArgumentException("maxUnits must be at least 1, but was " + maxUnits);
        }
        if (unit.isEmpty() && maxUnits != 1) {
            throw new IllegalArgumentException(
                    "maxUnits is " + maxUnits + " but no unit is named; a maximum of what?");
        }
    }

    /** A limit with no per-unit dimension, such as {@code DED-80GG-001} limb (b). */
    public static PerUnitPerPeriod perPeriod(Money amount, BaseId period) {
        return new PerUnitPerPeriod(amount, Optional.empty(), 1, period);
    }

    /** A limit per unit per period, such as children's education allowance. */
    public static PerUnitPerPeriod perUnitPerPeriod(Money amount, BaseId unit, int maxUnits, BaseId period) {
        return new PerUnitPerPeriod(amount, Optional.of(unit), maxUnits, period);
    }

    @Override
    public CapResult apply(CapContext ctx) {
        int periods = ctx.count(period);
        if (periods < 0) {
            throw new IllegalArgumentException("Negative period count " + periods + " for " + period);
        }
        int units = 1;
        if (unit.isPresent()) {
            int declared = ctx.count(unit.get());
            if (declared < 0) {
                throw new IllegalArgumentException("Negative unit count " + declared + " for " + unit.get());
            }
            units = Math.min(declared, maxUnits);
        }
        return new CapResult(amount.times(units * periods), shapeName());
    }

    @Override
    public String shapeName() {
        return unit.map(u -> amount + " per " + period + " per " + u + ", max " + maxUnits)
                .orElseGet(() -> amount + " per " + period);
    }
}
