package com.thiru.wealthlens.taxengine.api;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.List;
import java.util.Optional;

/**
 * The least-tax allocation of an {@link AllocationSpace}, every figure measured by full computation, never modelled
 * ({@code 06-algorithms.md} G6.1; W-238a). The regime is part of the answer: it is chosen on every allocation tried
 * (A2.1), so the regime before and after can differ.
 *
 * @param regimeBefore the regime the rules choose on the pay as entered
 * @param billBefore that regime's net payable
 * @param regimeAfter the regime the rules choose on the suggested pay
 * @param billAfter that regime's net payable — never more than {@code billBefore}
 * @param changes each movable component whose amount changes, in code order
 * @param residualAfter the residual component's amount after every change
 * @param verified the components the exhaustive check covered, recorded because the breadth is a tuning knob (G6.3)
 * @param pipelineRuns the whole computations run to reach the answer
 * @param discrepancy where the greedy answer was not the least, the notice saying so — a sign the candidates miss a
 *     breakpoint (G6.3)
 */
public record Optimisation(String regimeBefore, Money billBefore, String regimeAfter, Money billAfter,
        List<Change> changes, Money residualAfter, List<String> verified, int pipelineRuns, Optional<String> discrepancy) {

    public Optimisation {
        changes = List.copyOf(changes);
        verified = List.copyOf(verified);
    }

    /**
     * One component's suggested amount. {@code savingAlone} is this change made by itself, funded from the residual;
     * components interact (a shared cap, the regime), so the savings need not add up to the whole saving.
     *
     * @param component the component's code
     * @param current its amount as entered
     * @param suggested its amount in the answer
     * @param savingAlone the bill as entered less the bill with only this change; empty where the residual as entered
     *     cannot fund this change by itself
     */
    public record Change(String component, Money current, Money suggested, Optional<Money> savingAlone) {
    }
}
