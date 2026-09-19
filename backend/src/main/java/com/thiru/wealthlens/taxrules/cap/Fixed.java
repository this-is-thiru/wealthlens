package com.thiru.wealthlens.taxrules.cap;

import com.thiru.wealthlens.taxrules.money.Money;

/**
 * A flat statutory amount, such as the &#8377;75,000 standard deduction.
 *
 * <p>Reads nothing from the context: the amount is the answer. It is still a {@link CapShape} rather
 * than a bare {@code Money} field on a rule row, so that every limit in the pack is reached the same
 * way and a rule can be changed from a flat figure to a ladder without changing the code that
 * applies it.
 *
 * @param amount the figure as the statute states it
 */
public record Fixed(Money amount) implements CapShape {

    public Fixed {
        if (amount == null) {
            throw new IllegalArgumentException("A Fixed cap amount may not be null");
        }
    }

    @Override
    public CapResult apply(CapContext ctx) {
        return new CapResult(amount, shapeName());
    }

    @Override
    public String shapeName() {
        return "fixed";
    }
}
