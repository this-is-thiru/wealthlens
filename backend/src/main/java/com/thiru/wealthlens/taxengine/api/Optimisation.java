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
 * @param fundChanges each movable fund whose contribution changes, by fund name (W-238c)
 * @param residualAfter the residual component's amount after every change
 * @param verified the components the exhaustive check covered, recorded because the breadth is a tuning knob (G6.3)
 * @param pipelineRuns the whole computations run to reach the answer
 * @param discrepancy where the greedy answer was not the least, the notice saying so — a sign the candidates miss a
 *     breakpoint (G6.3)
 */
public record Optimisation(String regimeBefore, Money billBefore, String regimeAfter, Money billAfter,
        List<Change> changes, List<FundChange> fundChanges, Money residualAfter, List<String> verified, int pipelineRuns, Optional<String> discrepancy) {

    public Optimisation {
        changes = List.copyOf(changes);
        fundChanges = List.copyOf(fundChanges);
        verified = List.copyOf(verified);
    }

    /**
     * One component's suggested amount. {@code savingAlone} is this change made by itself, funded from the residual;
     * components interact (a shared cap, the regime), so the savings need not add up to the whole saving.
     *
     * @param component the component's code
     * @param facts what the year's pack says the component is, so a reader outside the engine can show it (W-238b)
     * @param current its amount as entered
     * @param suggested its amount in the answer
     * @param savingAlone the bill as entered less the bill with only this change; empty where the residual as entered
     *     cannot fund this change by itself
     * @param cashImpact the change in cash before tax when the residual funds this move: what the move costs in hand,
     *     which a saving in tax does not show (A2.6, D-CASHFLAG)
     */
    public record Change(String component, ComponentFacts facts, Money current, Money suggested,
            Optional<Money> savingAlone, Money cashImpact) {
    }

    /**
     * One employer contribution's suggested amount (W-238c), its saving measured as {@link Change}'s. A contribution is not
     * a component of pay, so it has no row in the pack's components and carries no {@link ComponentFacts}.
     *
     * @param fund the fund's {@code EmployerFund} name
     * @param current the contribution as entered, nil where none was
     * @param suggested the contribution in the answer
     * @param savingAlone the bill as entered less the bill with only this change; empty where the residual as entered
     *     cannot fund it by itself
     * @param cashImpact the change in cash before tax: the residual's pay given up, since a contribution is never in hand
     */
    public record FundChange(String fund, Money current, Money suggested, Optional<Money> savingAlone, Money cashImpact) {
    }

    /**
     * A component's row in the year's pack, published as facts, not as a verdict drawn from them (CLAUDE.md §4).
     *
     * @param kind its {@code ComponentKind}'s name
     * @param cashNature its {@code CashNature}'s name
     * @param feedsInto the rules it feeds, sorted
     */
    public record ComponentFacts(String kind, String cashNature, List<String> feedsInto) {

        public ComponentFacts {
            feedsInto = List.copyOf(feedsInto);
        }
    }
}
