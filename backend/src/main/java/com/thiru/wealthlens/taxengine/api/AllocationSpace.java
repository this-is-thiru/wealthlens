package com.thiru.wealthlens.taxengine.api;

import com.thiru.wealthlens.taxengine.input.EmployerFund;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Set;

/**
 * What the optimiser may change ({@code 03-advisory.md} A2.1, A2.2; W-238a): the pay of {@code base}, of which each
 * movable component may take money from {@code residual} or give it back, so the employment's total — the budget, C1 —
 * never changes and no amount goes below zero (C2). Cash take-home after tax may not fall below {@code cashFloor} (C3).
 * The final exhaustive check covers the {@code verificationBreadth} components that priced highest (A2.5), the rest held
 * at the greedy answer.
 *
 * @param base the inputs as entered; their regime is only a starting point, since the regime is chosen on every allocation
 * @param moves what may move: pay, employer funds and the employee's own contributions (W-238k)
 * @param residual the taxable balancing component that funds every move of pay or fund, itself pay ({@code SALARY})
 * @param cashFloor the least cash take-home after tax the user accepts
 * @param verificationBreadth how many components the exhaustive check covers
 * @param providentFundTerms whether each provident fund share of the residual's employment follows basic (W-238i)
 */
public record AllocationSpace(EngineInputs base, Moves moves, ComponentCode residual, Money cashFloor,
        int verificationBreadth, ProvidentFundTerms providentFundTerms) {

    /**
     * What the employer and the employee let move. Three kinds, because each is paid differently: pay and an employer's
     * contribution come out of the cost to company through the residual (C1), an employee's own contribution out of
     * take-home (C3; W-238k).
     *
     * @param movable the components the employer will let move, each pay ({@code SALARY} in the year's pack, W-238g); a
     *     code not yet in the employment starts at nil
     * @param movableFunds the funds the employer will let pay into for the residual's employment, at nil where none is
     *     entered (W-238c); a contribution is never take-home (C11), so the cash floor binds it
     * @param contributions the employee's own contributions that may rise above what is entered, each an
     *     {@code EMPLOYEE_CONTRIBUTION} in the year's pack, paid from take-home and never by the residual (W-238k C1a, C2a)
     */
    public record Moves(Set<ComponentCode> movable, Set<EmployerFund> movableFunds, Set<ComponentCode> contributions) {

        public Moves {
            if (movable == null || movableFunds == null || contributions == null) {
                throw new IllegalArgumentException("The moves need the movable components, funds and contributions;"
                        + " an empty set where none may move");
            }
            if (movable.isEmpty() && movableFunds.isEmpty() && contributions.isEmpty()) {
                throw new IllegalArgumentException("Name at least one component, fund or contribution the optimiser may"
                        + " move");
            }
            movable = Set.copyOf(movable);
            movableFunds = Set.copyOf(movableFunds);
            contributions = Set.copyOf(contributions);
        }
    }

    public AllocationSpace {
        if (base == null || moves == null || residual == null || cashFloor == null) {
            throw new IllegalArgumentException("An allocation space needs the inputs, the moves, the residual component"
                    + " and a cash floor");
        }
        if (moves.movableFunds().contains(EmployerFund.RECOGNISED_PROVIDENT_FUND)) {
            throw new IllegalArgumentException("The employer's provident fund contribution follows basic pay (A2.2 C8):"
                    + " name BASIC movable, and the contribution follows it");
        }
        if (moves.movable().contains(residual)) {
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
        requireTerms(providentFundTerms);
    }

    private static void requireTerms(ProvidentFundTerms providentFundTerms) {
        if (providentFundTerms == null) {
            throw new IllegalArgumentException("An allocation space needs the provident fund terms;"
                    + " ProvidentFundTerms.FOLLOW_BASIC where both shares follow basic");
        }
    }
}
