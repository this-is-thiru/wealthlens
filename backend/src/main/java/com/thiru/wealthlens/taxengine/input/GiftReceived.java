package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Optional;

/**
 * One sum of money or one property received in the tax year without, or for inadequate, consideration (S2
 * s.92(2)(m); 1961 s.56(2)(x); D-201).
 *
 * <p>The value is entered as the person has it rather than worked out here: a property's stamp duty value is the
 * stamp authority's, on the date of agreement where the Act allows it (S2 s.92(4)(a)), and another property's fair
 * market value follows a prescribed method (S4 r.57). Which limb reaches a receipt, by its kind and whether a
 * consideration was paid, is the rows' (W-158d); a sum received for one reaches none and is refused there.
 *
 * @param kind what was received
 * @param consideration what was paid for it; nil where nothing was
 * @param value the sum, or the property's stamp duty value or fair market value
 * @param excludedAs the ground on which it is outside the charge, if one is claimed
 */
public record GiftReceived(GiftKind kind, Money consideration, Money value, Optional<GiftExclusion> excludedAs) {

    public GiftReceived {
        if (kind == null || excludedAs == null) {
            throw new IllegalArgumentException("A gift received needs its kind, and Optional.empty() where no ground"
                    + " of exclusion is claimed");
        }
        for (Money figure : new Money[] {consideration, value}) {
            if (figure == null || figure.isLessThan(Money.ZERO)) {
                throw new IllegalArgumentException("A gift received needs its consideration and value, neither"
                        + " negative; enter 0 where nil");
            }
        }
    }
}
