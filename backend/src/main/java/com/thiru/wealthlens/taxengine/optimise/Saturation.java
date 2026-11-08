package com.thiru.wealthlens.taxengine.optimise;

import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Optional;
import java.util.function.Function;

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
 *
 * <p>An employee's own contribution is measured otherwise (W-238k C3a): its rules need not be convex. Own NPS takes only
 * what the 10 % sub-cap left ({@code DED-NPS-OWN}; {@code 08-golden-tests.md} T5-1), so where the Schedule XV ceiling
 * binds first, total income is flat between the two and then falls again, and the bisection above would stop at the
 * flat stretch. Its point is instead the least addition at which total income reaches what it is at the ceiling.
 */
final class Saturation {

    private static final Money RUPEE = Money.of("1");

    private Saturation() {
    }

    static Money of(AllocationPricing pricing, Allocation from, Movable code, RegimeCode regime) {
        if (code instanceof Movable.Contribution) {
            Money ceiling = wholeRupees(pricing.cashBeforeTax(from).plus(from.amount(code)));
            return leastReaching(ceiling, amount -> pricing.totalIncome(from.moved(code, amount), regime)
                    .orElseThrow(() -> new IllegalStateException("A contribution of " + amount + " to " + code.name()
                            + " could not be priced, though the pay it is added to was")));
        }
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

    /**
     * The least whole-rupee amount up to {@code ceiling} at which {@code totalIncome} reaches its value at the ceiling: a
     * bisection, sound wherever total income never rises as the amount does, as for a deduction.
     *
     * @throws IllegalStateException if total income at the ceiling is above total income at nil, so it rises somewhere
     *     and the bisection would not be sound
     */
    static Money leastReaching(Money ceiling, Function<Money, Money> totalIncome) {
        Money least = totalIncome.apply(ceiling);
        if (least.isGreaterThan(totalIncome.apply(Money.ZERO))) {
            throw new IllegalStateException("Total income rises from " + totalIncome.apply(Money.ZERO) + " at nil to "
                    + least + " at " + ceiling + ", so the least amount reaching it cannot be found by halving (W-238k C3a)");
        }
        Money low = Money.ZERO;
        Money high = ceiling;
        while (low.isLessThan(high)) {
            Money middle = wholeRupees(low.plus(high).scaledBy(1, 2));
            if (totalIncome.apply(middle).isGreaterThan(least)) {
                low = middle.plus(RUPEE);
            } else {
                high = middle;
            }
        }
        return low;
    }

    /** Paise dropped: rounding to a multiple of one rupee, up only from a whole rupee left over, is a floor. */
    private static Money wholeRupees(Money amount) {
        return amount.roundToMultiple(RUPEE, RUPEE);
    }
}
