package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Arrays;
import java.util.List;

/**
 * A capital loss carried forward from the tax year it arose, as earlier years left it (S2 s.111, s.536(2)(n); 1961
 * s.74; D-247). Consumed, never remaining: what is left is the engine's subtraction (M3.4).
 *
 * <p>The two terms are kept apart because they are set off differently: a short-term loss against any capital gain,
 * a long-term loss only against long-term gains (S2 s.111(1)(a)). The return condition is a fact about the year of
 * origin the engine cannot see, so it is stated here (S2 s.121; 1961 s.80).
 *
 * @param shortTermLoss the short-term loss carried forward from that year
 * @param longTermLoss the long-term loss carried forward from that year
 * @param shortTermAbsorbedSince how much of the short-term loss later years have already set off
 * @param longTermAbsorbedSince how much of the long-term loss later years have already set off
 * @param determinedInReturnFiledInTime whether the loss was determined in a return filed under S2 s.263(1) / 1961
 *     s.139(3); a loss that was not is not carried forward at all
 */
public record CarriedCapitalLoss(Money shortTermLoss, Money longTermLoss, Money shortTermAbsorbedSince,
        Money longTermAbsorbedSince, boolean determinedInReturnFiledInTime) {

    public CarriedCapitalLoss {
        List<Money> figures = Arrays.asList(shortTermLoss, longTermLoss, shortTermAbsorbedSince,
                longTermAbsorbedSince);
        if (figures.contains(null) || figures.stream().anyMatch(figure -> figure.isLessThan(Money.ZERO))) {
            throw new IllegalArgumentException("A carried capital loss needs every figure, none negative; enter 0"
                    + " where nil, but was " + figures);
        }
        if (shortTermAbsorbedSince.isGreaterThan(shortTermLoss) || longTermAbsorbedSince.isGreaterThan(longTermLoss)) {
            throw new IllegalArgumentException("What later years absorbed of a carried capital loss is at most the"
                    + " loss, but was " + figures);
        }
    }

    /** The short-term loss still to be set off. */
    public Money shortTermRemaining() {
        return shortTermLoss.minus(shortTermAbsorbedSince);
    }

    /** The long-term loss still to be set off. */
    public Money longTermRemaining() {
        return longTermLoss.minus(longTermAbsorbedSince);
    }
}
