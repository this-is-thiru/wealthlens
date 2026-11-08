package com.thiru.wealthlens.taxengine.optimise;

import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * One way of dividing the movable pay: the amount of each movable component and of the residual that funds them. A
 * value, so a priced allocation can be remembered and never computed twice.
 *
 * @param amounts each movable component's amount and the residual's, in code order
 * @param residual the funding component's code
 */
record Allocation(SortedMap<ComponentCode, Money> amounts, ComponentCode residual) {

    static final Comparator<ComponentCode> CODE_ORDER = Comparator.comparing(ComponentCode::value);

    Allocation {
        amounts = Collections.unmodifiableSortedMap(copyOf(amounts));
    }

    Money amount(ComponentCode code) {
        return amounts.get(code);
    }

    Money residualAmount() {
        return amounts.get(residual);
    }

    /** {@code code} set to {@code target}, the difference taken from or returned to the residual. */
    Allocation moved(ComponentCode code, Money target) {
        SortedMap<ComponentCode, Money> next = copyOf(amounts);
        next.put(residual, residualAmount().minus(target.minus(amount(code))));
        next.put(code, target);
        return new Allocation(next, residual);
    }

    /** Whether no amount is below nil — C2, the residual included. */
    boolean isFeasible() {
        return amounts.values().stream().noneMatch(amount -> amount.isLessThan(Money.ZERO));
    }

    private static SortedMap<ComponentCode, Money> copyOf(Map<ComponentCode, Money> amounts) {
        SortedMap<ComponentCode, Money> copy = new TreeMap<>(CODE_ORDER);
        copy.putAll(amounts);
        return copy;
    }
}
