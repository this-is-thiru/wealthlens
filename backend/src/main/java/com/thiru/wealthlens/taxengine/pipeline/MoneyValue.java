package com.thiru.wealthlens.taxengine.pipeline;

import com.thiru.wealthlens.taxrules.money.Money;

/**
 * A node whose output is one amount — gross salary, total income, cess.
 *
 * <p>The common case, and a record rather than a bare {@code Money} so that every node produces a
 * {@link NodeValue} and the pipeline never has to ask which kind it got.
 *
 * @param amount the figure
 */
public record MoneyValue(Money amount) implements NodeValue {

    public MoneyValue {
        if (amount == null) {
            throw new IllegalArgumentException("A MoneyValue amount may not be null");
        }
    }

    public static MoneyValue of(Money amount) {
        return new MoneyValue(amount);
    }

    @Override
    public Money asMoney() {
        return amount;
    }
}
