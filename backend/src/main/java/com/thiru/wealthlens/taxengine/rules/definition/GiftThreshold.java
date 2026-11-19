package com.thiru.wealthlens.taxengine.rules.definition;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import java.util.Optional;

/**
 * How much may be received without, or for inadequate, consideration before it is income, for one limb of the charge
 * on gifts (S2 s.92(2)(m)(i)–(iii); 1961 s.56(2)(x)(a)–(c); D-201, D-352): the amount the limb's excess must exceed,
 * the share of the consideration that may raise it, and whether the limb tests the year's total or each receipt. Each
 * limb carries its own, as the Act states each limb's own amount.
 *
 * @param exceeds the amount the excess must exceed — "exceeds", so equal is not
 * @param considerationShare the share of the consideration the tolerance may rise to, where the limb has one (immovable
 *     property, S2 s.92(2)(m)(ii)(B)(II)); {@code Optional.empty()} where it does not
 * @param aggregation whether the limb's receipts are totalled for the year or tested one by one
 */
public record GiftThreshold(Money exceeds, Optional<Rate> considerationShare, GiftAggregation aggregation)
        implements RuleDefinition {

    public GiftThreshold {
        if (exceeds == null || !exceeds.isGreaterThan(Money.ZERO) || considerationShare == null
                || considerationShare.filter(share -> share.compareTo(Rate.ZERO) <= 0).isPresent()
                || aggregation == null) {
            throw new IllegalArgumentException("A gift threshold needs an amount above nil, a share of consideration"
                    + " above nil or Optional.empty(), and its aggregation, but was " + exceeds + ", "
                    + considerationShare + " and " + aggregation);
        }
    }

    /**
     * The value beyond the consideration, where that excess is more than the higher of the amount and the limb's share
     * of the consideration; nil otherwise (S2 s.92(2)(m)(ii)(B), (iii)(B)). A year's total and a receipt without
     * consideration carry nil consideration, so their charge is the whole value above the amount — "the whole of such
     * sum", "the stamp duty value of such property" ((i), (ii)(A), (iii)(A)).
     */
    public Money charged(Money consideration, Money value) {
        Money excess = value.minus(consideration);
        Money tolerance = considerationShare.map(share -> Money.max(exceeds, share.of(consideration))).orElse(exceeds);
        return excess.isGreaterThan(tolerance) ? excess : Money.ZERO;
    }
}
