package com.thiru.wealthlens.taxengine.api;

import com.thiru.wealthlens.taxengine.input.EmployerFund;
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
 * @param movableFunds the funds the employer will let pay into for the residual's employment, at nil where none is
 *     entered (W-238c); a contribution is never take-home (C11), so the cash floor binds it
 * @param residual the taxable balancing component that funds every move
 * @param cashFloor the least cash take-home after tax the user accepts
 * @param verificationBreadth how many components the exhaustive check covers
 */
public record AllocationSpace(EngineInputs base, Set<ComponentCode> movable, Set<EmployerFund> movableFunds,
        ComponentCode residual, Money cashFloor, int verificationBreadth) {

    public AllocationSpace {
        if (base == null || movable == null || movableFunds == null || residual == null || cashFloor == null) {
            throw new IllegalArgumentException("An allocation space needs the inputs, the movable components and funds,"
                    + " the residual component and a cash floor");
        }
        if (movable.isEmpty() && movableFunds.isEmpty()) {
            throw new IllegalArgumentException("Name at least one component or fund the optimiser may move");
        }
        if (movableFunds.contains(EmployerFund.RECOGNISED_PROVIDENT_FUND)) {
            throw new IllegalArgumentException("The employer's provident fund contribution follows basic pay (A2.2 C8), so"
                    + " it moves only with basic, which the optimiser does not yet do (W-238d)");
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
        movableFunds = Set.copyOf(movableFunds);
    }
}
