package com.thiru.wealthlens.taxengine.rules.cap;

import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.money.Rate;

/**
 * A percentage of a named amount, such as thirty per cent of the annual value of a let property.
 *
 * <p>Carries the base as an identifier, not a figure — see {@link BaseId}. The rate is a
 * {@link Rate}, so the pack states the percentage the way the statute does and the division to a
 * fraction happens once, inside that type.
 *
 * <p>Rounding happens once, in {@link Rate#of}, at the point the percentage becomes an amount. A
 * percentage of a percentage rounded at each step and rounded once give different answers, and only
 * the second can be re-derived by hand from the rulebook.
 *
 * @param percent the rate as the statute states it
 * @param base which amount it is a percentage of
 */
public record PercentOfBase(Rate percent, BaseId base) implements CapShape {

    public PercentOfBase {
        if (percent == null) {
            throw new IllegalArgumentException("A PercentOfBase rate may not be null");
        }
        if (base == null) {
            throw new IllegalArgumentException("A PercentOfBase base may not be null");
        }
    }

    @Override
    public CapResult apply(CapContext ctx) {
        return new CapResult(percent.of(ctx.base(base)), shapeName());
    }

    /** Names the rate and the base, so a trace line reads {@code "30% of ANNUAL_VALUE"}. */
    @Override
    public String shapeName() {
        return percent.asPercentLabel() + " of " + base;
    }
}
