package com.thiru.wealthlens.taxengine.rules.relief;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.reason.Reason;

/**
 * What a relief gives: an amount to take off the tax — a rebate, or surcharge given back — and why.
 *
 * @param amount never negative; zero where the relief does not apply
 * @param limitedBy what decided the amount, the audit text for the trace
 * @param reason the same as facts, for the explanation (W-327e2, D-692); the words of {@link
 *     com.thiru.wealthlens.taxengine.rules.cap.CapResult}
 */
public record ReliefResult(Money amount, String limitedBy, Reason reason) {

    public ReliefResult {
        if (amount == null || amount.isLessThan(Money.ZERO)) {
            throw new IllegalArgumentException("A relief is an amount to take off, never negative, but was " + amount);
        }
        if (limitedBy == null || limitedBy.isBlank() || reason == null) {
            throw new IllegalArgumentException("A relief must say why it gave what it gave, as text and as a reason");
        }
    }
}
