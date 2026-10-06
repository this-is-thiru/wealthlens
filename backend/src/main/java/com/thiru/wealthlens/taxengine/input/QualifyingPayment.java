package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.money.Money;

/**
 * A sum paid in the tax year that Chapter VIII lets the payer deduct — a Schedule XV investment or a pension deposit
 * for a minor (S2 s.123, s.124(4); D-130, D-132). Income under no head, so it is entered here rather than on an
 * employment; the employee's own contributions withheld from pay are pay lines instead.
 *
 * @param code a {@code QUALIFYING_PAYMENT} component live in the tax year
 * @param amount the amount paid or deposited in the tax year
 */
public record QualifyingPayment(ComponentCode code, Money amount) {

    public QualifyingPayment {
        if (code == null || amount == null) {
            throw new IllegalArgumentException("A qualifying payment needs its code and amount");
        }
        if (amount.isLessThan(Money.ZERO)) {
            throw new IllegalArgumentException("A qualifying payment may not be negative: " + code + " " + amount);
        }
    }
}
