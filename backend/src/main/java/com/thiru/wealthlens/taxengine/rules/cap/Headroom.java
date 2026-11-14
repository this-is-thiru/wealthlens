package com.thiru.wealthlens.taxengine.rules.cap;

import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.money.Money;

/**
 * What would change a least-of's figure (W-326a, D-696): how much more of the one amount that raises the binding limb
 * would bring it to the next limit, and that limit. A fact, carried on the line for an explanation's third sentence
 * (03-advisory A4.4); which amounts are worth saying so for is the content's choice, not the engine's.
 *
 * @param input the amount that raises the binding limb, and no other limb
 * @param more how much more of it reaches the next limit, exact
 * @param next the next limit's value, which the figure would then be held to
 */
public record Headroom(BaseId input, Money more, Money next) {

    public Headroom {
        if (input == null || more == null || next == null) {
            throw new IllegalArgumentException("A headroom needs its input, how much more and the next limit");
        }
        if (!more.isGreaterThan(Money.ZERO)) {
            throw new IllegalArgumentException("A headroom is more of an input, so above nil, but was " + more);
        }
    }
}
