package com.thiru.wealthlens.taxengine.optimise;

import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Optional;

/**
 * A movable item's saturation point, found by measurement rather than by a formula ({@code 03-advisory.md} A2.4 step 2;
 * W-238 C1a): the least whole-rupee amount beyond which one more rupee moved into it from the residual no longer lowers
 * total income under a regime. A formula would be the law a second time in Java (CLAUDE.md §4); measuring reads the
 * law only through the pack's rows.
 *
 * <p>The search is a bisection on "the next rupee stops lowering total income". That is sound where total income is
 * convex in the one component — each cap A2.3 lists (a least-of, an aggregate cap, an excess taxed back) makes it so —
 * since then the slope only rises. Where it is not, the greedy answer can miss, and the exhaustive check is what
 * catches and reports it (G6.3).
 *
 * <p>An amount the engine cannot price for want of the funds' history counts as stopping: a larger contribution only
 * adds to the aggregate, so every amount past the first unpriced one is unpriced too, and the bisection stays sound
 * (W-238h C1a).
 */
final class Saturation {

    private static final Money RUPEE = Money.of("1");

    private Saturation() {
    }

    static Money of(AllocationPricing pricing, Allocation from, Movable code, RegimeCode regime) {
        Money low = Money.ZERO;
        Money high = wholeRupees(from.ceiling(code));
        while (low.isLessThan(high)) {
            Money middle = wholeRupees(low.plus(high).scaledBy(1, 2));
            if (stopsFalling(pricing, from, code, regime, middle)) {
                high = middle;
            } else {
                low = middle.plus(RUPEE);
            }
        }
        return low;
    }

    private static boolean stopsFalling(AllocationPricing pricing, Allocation from, Movable code, RegimeCode regime,
            Money amount) {
        Optional<Money> here = pricing.totalIncome(from.moved(code, amount), regime);
        Optional<Money> next = pricing.totalIncome(from.moved(code, amount.plus(RUPEE)), regime);
        if (here.isEmpty() || next.isEmpty()) {
            return true;
        }
        return !next.get().isLessThan(here.get());
    }

    /** Paise dropped: rounding to a multiple of one rupee, up only from a whole rupee left over, is a floor. */
    private static Money wholeRupees(Money amount) {
        return amount.roundToMultiple(RUPEE, RUPEE);
    }
}
