package com.thiru.wealthlens.taxengine.optimise;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * One way of dividing the movable pay: the amount of each movable item, of what follows basic, and of the residual
 * that funds them. A value, so a priced allocation can be remembered and never computed twice.
 *
 * @param amounts each movable item's amount, each follower's and the residual's, in {@link Movable#ORDER}
 * @param residual the funding component
 * @param following what moves with its leader and by how much (A2.2 C8; W-238d)
 */
record Allocation(SortedMap<Movable, Money> amounts, Movable.Pay residual, Following following) {

    Allocation {
        amounts = Collections.unmodifiableSortedMap(copyOf(amounts));
    }

    Money amount(Movable code) {
        return amounts.get(code);
    }

    Money residualAmount() {
        return amounts.get(residual);
    }

    /**
     * {@code code} set to {@code target}, the difference taken from or returned to the residual. Where {@code code} leads,
     * its followers move with it, and the residual funds the employer's share too (C1, C8).
     */
    Allocation moved(Movable code, Money target) {
        SortedMap<Movable, Money> next = copyOf(amounts);
        Money taken = target.minus(amount(code));
        next.put(code, target);
        if (code.equals(following.leader())) {
            for (Movable follower : following.followers().keySet()) {
                Money to = following.amountAt(follower, target);
                if (Following.isFundedByResidual(follower)) {
                    taken = taken.plus(to.minus(amount(follower)));
                }
                next.put(follower, to);
            }
        }
        next.put(residual, residualAmount().minus(taken));
        return new Allocation(next, residual, following);
    }

    /** The most {@code code} can reach before the residual is spent. */
    Money ceiling(Movable code) {
        return code.equals(following.leader())
                ? following.ceiling(amount(code), residualAmount())
                : amount(code).plus(residualAmount());
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
