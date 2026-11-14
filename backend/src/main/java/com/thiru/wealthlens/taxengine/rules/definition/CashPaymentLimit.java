package com.thiru.wealthlens.taxengine.rules.definition;

import com.thiru.wealthlens.taxengine.rules.money.Money;

/**
 * The most a payment may be in cash and still be allowed — s.133(5)'s "Any deduction for a donation over ₹ 2000 shall
 * be allowed only if the payment is made by a mode other than cash" (S2; D-142). Not a cap: a cash payment over it is
 * allowed nothing, not the limit, so it is a definition the node applies to each payment.
 *
 * @param atMost the largest cash payment still allowed
 */
public record CashPaymentLimit(Money atMost) implements RuleDefinition {

    public CashPaymentLimit {
        if (atMost == null || atMost.isLessThan(Money.ZERO)) {
            throw new IllegalArgumentException("A cash payment limit must be nil or more, but was " + atMost);
        }
    }

    /** Whether a payment of {@code amount}, in cash or not, is allowed under the limit. */
    public boolean allows(Money amount, boolean paidInCash) {
        return !paidInCash || !amount.isGreaterThan(atMost);
    }
}
