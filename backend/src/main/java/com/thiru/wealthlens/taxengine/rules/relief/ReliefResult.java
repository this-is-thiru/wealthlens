package com.thiru.wealthlens.taxengine.rules.relief;

import com.thiru.wealthlens.taxengine.rules.money.Money;

/**
 * What a relief gives: an amount to take off the tax — a rebate, or surcharge given back — and why.
 *
 * @param amount never negative; zero where the relief does not apply
 * @param reason what decided the amount, for the trace
 */
public record ReliefResult(Money amount, String reason) {

    public ReliefResult {
        if (amount == null || amount.isLessThan(Money.ZERO)) {
            throw new IllegalArgumentException("A relief is an amount to take off, never negative, but was " + amount);
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A relief must say why it gave what it gave");
        }
    }
}
