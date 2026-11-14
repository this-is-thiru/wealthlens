package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.money.Money;

/**
 * The person's answer to one declaration of the year's catalogue (W-090a1, D-517): yes or no, or an amount. A question
 * not answered has no {@code Declaration} at all — the third state, which a condition reading it refuses rather than
 * reads as "no".
 */
public sealed interface Declaration {

    /**
     * A yes-or-no answer.
     *
     * @param yes whether the person answered yes
     */
    record YesNo(boolean yes) implements Declaration {
    }

    /**
     * An amount answer, in rupees.
     *
     * @param amount the amount declared, nil or more
     */
    record Amount(Money amount) implements Declaration {

        public Amount {
            if (amount == null || amount.isLessThan(Money.ZERO)) {
                throw new IllegalArgumentException("A declared amount must be nil or more, but was " + amount);
            }
        }
    }
}
