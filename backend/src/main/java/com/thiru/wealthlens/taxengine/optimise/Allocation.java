package com.thiru.wealthlens.taxengine.optimise;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * One way of dividing the movable pay: the amount of each movable item and of the residual that funds them. A
 * value, so a priced allocation can be remembered and never computed twice.
 *
 * @param amounts each movable item's amount and the residual's, in {@link Movable#ORDER}
 * @param residual the funding component
 */
record Allocation(SortedMap<Movable, Money> amounts, Movable.Pay residual) {

    Allocation {
        amounts = Collections.unmodifiableSortedMap(copyOf(amounts));
    }

    Money amount(Movable code) {
        return amounts.get(code);
    }

    Money residualAmount() {
        return amounts.get(residual);
    }

    /** {@code code} set to {@code target}, the difference taken from or returned to the residual. */
    Allocation moved(Movable code, Money target) {
        SortedMap<Movable, Money> next = copyOf(amounts);
        next.put(residual, residualAmount().minus(target.minus(amount(code))));
        next.put(code, target);
        return new Allocation(next, residual);
    }

    /** Whether no amount is below nil — C2, the residual included. */
    boolean isFeasible() {
        return amounts.values().stream().noneMatch(amount -> amount.isLessThan(Money.ZERO));
    }

    private static SortedMap<Movable, Money> copyOf(Map<Movable, Money> amounts) {
        SortedMap<Movable, Money> copy = new TreeMap<>(Movable.ORDER);
        copy.putAll(amounts);
        return copy;
    }
}
