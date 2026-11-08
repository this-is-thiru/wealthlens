package com.thiru.wealthlens.taxengine.optimise;

import com.thiru.wealthlens.taxengine.api.AllocationSpace;
import com.thiru.wealthlens.taxengine.api.Optimisation;
import com.thiru.wealthlens.taxengine.pipeline.NodeRegistry;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.regime.RegimeSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The salary-structure optimiser ({@code 03-advisory.md} A2.4 steps 3 – 6, {@code 06-algorithms.md} G6; W-238a).
 * Candidates per component are nil, the amount entered and the saturation point measured under each regime offered;
 * a greedy fill takes the move that saves the most tax per rupee moved, re-pricing every move after each one because
 * components interact; a bounded exhaustive check then covers the components that priced highest. Every price is a full
 * computation with the regime chosen inside it (G6.1, A2.1) — the objective is evaluated, never modelled.
 */
public final class SalaryOptimiser {

    private SalaryOptimiser() {
    }

    /** The greedy answer and, for each component, the most its best first move saved — what ranks the verification. */
    private record Greedy(Allocation answer, Map<Movable, Money> firstValue) {
    }

    /** {@code savingPerMillion}: the tax saved per ₹10,00,000 moved — a ratio carried as money to stay exact to paise. */
    private record Move(Allocation to, Money savingPerMillion) {
    }

    private static final Money MILLION = Money.of("1000000");

    /**
     * @throws IllegalArgumentException if the space does not fit the year's pack or the inputs, or the pay as entered
     *     already leaves less cash than the floor
     */
    public static Optimisation optimise(NodeRegistry registry, RulePack pack, RegimeSpec computing, AllocationSpace space) {
        AllocationPricing pricing = AllocationPricing.over(registry, pack, computing, space);
        Allocation entered = pricing.entered(space);
        Money takeHome = pricing.takeHome(entered);
        if (takeHome.isLessThan(space.cashFloor())) {
            throw new IllegalArgumentException("The pay as entered already leaves " + takeHome + " in cash after tax,"
                    + " below the floor of " + space.cashFloor() + "; no reallocation of it can raise cash");
        }
        SortedMap<Movable, SortedSet<Money>> candidates = candidates(pricing, entered);
        Greedy greedy = greedy(pricing, entered, candidates, space.cashFloor());
        List<Movable> verified = greedy.firstValue().entrySet().stream()
                .sorted(Map.Entry.<Movable, Money>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey(Movable.ORDER)))
                .limit(space.verificationBreadth()).map(Map.Entry::getKey).toList();
        Allocation answer = exhaustive(pricing, greedy.answer(), candidates, verified, space.cashFloor());
        return result(pricing, entered, greedy.answer(), answer, verified);
    }

    private static SortedMap<Movable, SortedSet<Money>> candidates(AllocationPricing pricing, Allocation entered) {
        List<RegimeCode> offered = pricing.price(entered).offered();
        SortedMap<Movable, SortedSet<Money>> candidates = new TreeMap<>(Movable.ORDER);
        for (Movable code : entered.amounts().keySet()) {
            if (code.equals(entered.residual()) || entered.following().follows(code)) {
                continue;
            }
            SortedSet<Money> points = new TreeSet<>(List.of(Money.ZERO, entered.amount(code)));
            offered.forEach(regime -> points.add(Saturation.of(pricing, entered, code, regime)));
            candidates.put(code, points);
        }
        return candidates;
    }

    private static Greedy greedy(AllocationPricing pricing, Allocation entered,
            SortedMap<Movable, SortedSet<Money>> candidates, Money floor) {
        Map<Movable, Money> firstValue = new TreeMap<>(Movable.ORDER);
        candidates.keySet().forEach(code -> firstValue.put(code, Money.ZERO));
        Allocation current = entered;
        boolean firstRound = true;
        while (true) {
            Money bill = pricing.price(current).bill();
            Optional<Move> best = Optional.empty();
            for (Map.Entry<Movable, SortedSet<Money>> each : candidates.entrySet()) {
                for (Money target : each.getValue()) {
                    Money step = target.minus(current.amount(each.getKey()));
                    Allocation next = current.moved(each.getKey(), target);
                    if (step.equals(Money.ZERO) || !admissible(pricing, next, floor)) {
                        continue;
                    }
                    Money saving = bill.minus(pricing.price(next).bill());
                    if (firstRound) {
                        firstValue.merge(each.getKey(), saving, Money::max);
                    }
                    if (!saving.isGreaterThan(Money.ZERO)) {
                        continue;
                    }
                    Money perMillion = MILLION.scaledBy(saving, Money.max(step, Money.ZERO.minus(step)));
                    if (best.map(move -> perMillion.isGreaterThan(move.savingPerMillion())).orElse(true)) {
                        best = Optional.of(new Move(next, perMillion));
                    }
                }
            }
            firstRound = false;
            if (best.isEmpty()) {
                return new Greedy(current, firstValue);
            }
            current = best.get().to();
        }
    }

    /** Every combination of the verified components' candidates, the rest held at the greedy answer (A2.5). */
    private static Allocation exhaustive(AllocationPricing pricing, Allocation greedy,
            SortedMap<Movable, SortedSet<Money>> candidates, List<Movable> verified, Money floor) {
        List<List<Money>> options = new ArrayList<>();
        for (Movable code : verified) {
            SortedSet<Money> points = new TreeSet<>(candidates.get(code));
            points.add(greedy.amount(code));
            options.add(List.copyOf(points));
        }
        Allocation least = greedy;
        Money leastBill = pricing.price(greedy).bill();
        int[] position = new int[verified.size()];
        do {
            Allocation trial = greedy;
            for (int i = 0; i < position.length; i++) {
                trial = trial.moved(verified.get(i), options.get(i).get(position[i]));
            }
            if (admissible(pricing, trial, floor) && pricing.price(trial).bill().isLessThan(leastBill)) {
                least = trial;
                leastBill = pricing.price(trial).bill();
            }
        } while (advance(position, options));
        return least;
    }

    private static boolean advance(int[] position, List<List<Money>> options) {
        for (int i = position.length - 1; i >= 0; i--) {
            if (++position[i] < options.get(i).size()) {
                return true;
            }
            position[i] = 0;
        }
        return false;
    }

    private static boolean admissible(AllocationPricing pricing, Allocation allocation, Money floor) {
        return allocation.isFeasible() && pricing.isPriced(allocation) && !pricing.takeHome(allocation).isLessThan(floor);
    }

    private static Optimisation result(AllocationPricing pricing, Allocation entered, Allocation greedy, Allocation answer,
            List<Movable> verified) {
        AllocationPricing.Priced before = pricing.price(entered);
        AllocationPricing.Priced after = pricing.price(answer);
        List<Optimisation.Change> changes = new ArrayList<>();
        List<Optimisation.FundChange> fundChanges = new ArrayList<>();
        List<Optimisation.FollowingChange> followingChanges = new ArrayList<>();
        for (Movable item : entered.amounts().keySet()) {
            Money current = entered.amount(item);
            Money suggested = answer.amount(item);
            if (item.equals(entered.residual()) || suggested.equals(current)) {
                continue;
            }
            if (entered.following().follows(item)) {
                followingChanges.add(new Optimisation.FollowingChange(item.name(), pricing.feedsInto(item), current,
                        suggested, entered.following().leader().name(), pricing.liquidity(item)));
                continue;
            }
            Allocation alone = entered.moved(item, suggested);
            Optional<Money> savingAlone = alone.isFeasible() && pricing.isPriced(alone)
                    ? Optional.of(before.bill().minus(pricing.price(alone).bill()))
                    : Optional.empty();
            Money cashImpact = pricing.cashImpact(item, entered, alone);
            switch (item) {
                case Movable.Pay pay -> changes.add(new Optimisation.Change(pay.name(), pricing.facts(pay), current,
                        suggested, savingAlone, cashImpact));
                case Movable.Fund fund -> fundChanges.add(new Optimisation.FundChange(fund.name(),
                        pricing.feedsInto(fund), current, suggested, savingAlone, cashImpact, pricing.liquidity(fund)));
            }
        }
        List<String> verifiedNames = verified.stream().map(Movable::name).toList();
        Optional<String> discrepancy = answer.equals(greedy) ? Optional.empty() : Optional.of("The greedy answer"
                + " (bill " + pricing.price(greedy).bill() + ") was not the least: the exhaustive check over "
                + verifiedNames + " found " + after.bill() + " — the candidates miss a breakpoint (G6.3)");
        return new Optimisation(before.regime().value(), before.bill(), after.regime().value(), after.bill(), changes,
                fundChanges, followingChanges, answer.residualAmount(), verifiedNames, pricing.pipelineRuns(), discrepancy,
                pricing.unpriced());
    }
}
