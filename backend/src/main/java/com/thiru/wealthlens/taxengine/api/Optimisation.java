package com.thiru.wealthlens.taxengine.api;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;
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
 * @param followingChanges each amount that changes only because what it follows moved, by name (W-238d)
 * @param residualAfter the residual component's amount after every change
 * @param verified the components the exhaustive check covered, recorded because the breadth is a tuning knob (G6.3)
 * @param pipelineRuns the whole computations run to reach the answer
 * @param discrepancy where the greedy answer was not the least, the notice saying so — a sign the candidates miss a
 *     breakpoint (G6.3)
 * @param unpriced where an allocation tried could not be priced for want of an entered fact, which fact and the rule
 *     that needs it; the search kept to what the engine could price, so entering the fact may widen the answer (W-238h)
 */
public record Optimisation(String regimeBefore, Money billBefore, String regimeAfter, Money billAfter,
        List<Change> changes, List<FundChange> fundChanges, List<FollowingChange> followingChanges, Money residualAfter,
        List<String> verified, int pipelineRuns, Optional<String> discrepancy, Optional<UnpricedNotice> unpriced) {

    public Optimisation {
        changes = List.copyOf(changes);
        fundChanges = List.copyOf(fundChanges);
        followingChanges = List.copyOf(followingChanges);
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
     *     cannot fund this change by itself, or the engine cannot price it alone (W-238h)
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
     * @param feedsInto every rule the contribution can feed, from the fund's row in the year's pack, sorted (W-238e)
     * @param current the contribution as entered, nil where none was
     * @param suggested the contribution in the answer
     * @param savingAlone the bill as entered less the bill with only this change; empty where the residual as entered
     *     cannot fund it by itself, or the engine cannot price it alone (W-238h)
     * @param cashImpact the change in cash before tax: the residual's pay given up, since a contribution is never in hand
     * @param liquidity when the money paid in can come out, from the fund's row in the year's pack (W-238e1)
     */
    public record FundChange(String fund, List<String> feedsInto, Money current, Money suggested,
            Optional<Money> savingAlone, Money cashImpact, Liquidity liquidity) {

        public FundChange {
            feedsInto = List.copyOf(feedsInto);
        }
    }

    /**
     * An amount that moves only with another (A2.2 C8; W-238d): a provident fund contribution kept at its entered ratio to
     * basic. It is no move a user can make alone, so it has no saving of its own; the change it follows carries the
     * saving and the cash impact of both.
     *
     * @param name the employee's component code or the employer's {@code EmployerFund} name
     * @param feedsInto every rule it can feed, from its component's or fund's row in the year's pack, sorted (W-238e)
     * @param current its amount as entered
     * @param suggested its amount in the answer
     * @param follows the component it follows
     * @param liquidity when the money paid in can come out, from its component's or fund's row (W-238e1)
     */
    public record FollowingChange(String name, List<String> feedsInto, Money current, Money suggested, String follows,
            Liquidity liquidity) {

        public FollowingChange {
            feedsInto = List.copyOf(feedsInto);
        }
    }

    /**
     * A component's row in the year's pack, published as facts, not as a verdict drawn from them (CLAUDE.md §4).
     *
     * @param kind its {@code ComponentKind}'s name
     * @param cashNature its {@code CashNature}'s name
     * @param feedsInto the rules it feeds, sorted
     * @param liquidity when money paid in can come out, for a contribution into a fund; no terms and no unstated
     *     periods for pay (W-238e1)
     */
    public record ComponentFacts(String kind, String cashNature, List<String> feedsInto, Liquidity liquidity) {

        public ComponentFacts {
            feedsInto = List.copyOf(feedsInto);
        }
    }

    /**
     * When money paid into a fund can come out, as the year's pack states it (A2.6 {@code liquidity}; W-238e1): facts
     * dated within the year, never a verdict such as "locked" drawn from them (CLAUDE.md §4).
     *
     * @param terms each withdrawal term overlapping the year, by first day then event
     * @param unstated each period of the year for which the pack states no terms, and why
     */
    public record Liquidity(List<WithdrawalTerm> terms, List<UnstatedTerms> unstated) {

        public Liquidity {
            terms = List.copyOf(terms);
            unstated = List.copyOf(unstated);
        }
    }

    /**
     * One withdrawal term.
     *
     * @param event when it applies, as the governing instrument names it
     * @param terms what may then be withdrawn, in the instrument's figures
     * @param from its first day
     * @param to its last day; empty where the pack states no end
     * @param source the Gazette copy and provision it was read from
     */
    public record WithdrawalTerm(String event, String terms, LocalDate from, Optional<LocalDate> to, String source) {
    }

    /**
     * A period without stated terms.
     *
     * @param from its first day
     * @param to its last day; empty where the pack states no end
     * @param reason why the pack states none
     */
    public record UnstatedTerms(LocalDate from, Optional<LocalDate> to, String reason) {
    }

    /**
     * Why some allocations were not tried (W-238h C2a): the engine refused them for want of an entered fact.
     *
     * @param missingInput the entered fact, by its name in the inputs
     * @param rule the rule whose computation needs it
     */
    public record UnpricedNotice(String missingInput, String rule) {
    }
}
