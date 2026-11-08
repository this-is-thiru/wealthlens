package com.thiru.wealthlens.taxengine.api;

import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Set;

/**
 * What the optimiser may change ({@code 03-advisory.md} A2.1, A2.2; W-238a): the pay of {@code base}, of which each
 * {@code movable} component may take money from {@code residual} or give it back, so the employment's total — the
 * budget, C1 — never changes and no amount goes below zero (C2). Cash take-home after tax may not fall below
 * {@code cashFloor} (C3). The final exhaustive check covers the {@code verificationBreadth} components that priced
 * highest (A2.5), the rest held at the greedy answer.
 *
 * @param base the inputs as entered; their regime is only a starting point, since the regime is chosen on every allocation
 * @param movable the components the employer will let move; a code not yet in the employment starts at nil
 * @param residual the taxable balancing component that funds every move
 * @param cashFloor the least cash take-home after tax the user accepts
 * @param verificationBreadth how many components the exhaustive check covers
 */
public record AllocationSpace(EngineInputs base, Set<ComponentCode> movable, ComponentCode residual, Money cashFloor,
        int verificationBreadth) {

    public AllocationSpace {
        if (base == null || movable == null || residual == null || cashFloor == null) {
            throw new IllegalArgumentException("An allocation space needs the inputs, the movable components, the residual"
                    + " component and a cash floor");
        }
        if (movable.isEmpty()) {
            throw new IllegalArgumentException("Name at least one component the optimiser may move");
        }
        if (movable.contains(residual)) {
            throw new IllegalArgumentException("The residual component " + residual.value()
                    + " funds the moves, so it cannot also be one of them");
        }
        if (cashFloor.isLessThan(Money.ZERO)) {
            throw new IllegalArgumentException("A cash floor cannot be negative: " + cashFloor);
        }
        if (verificationBreadth < 1) {
            throw new IllegalArgumentException("The exhaustive check must cover at least one component, not "
                    + verificationBreadth);
        }
        movable = Set.copyOf(movable);
    }
}
