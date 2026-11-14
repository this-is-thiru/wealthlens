package com.thiru.wealthlens.taxengine.optimise;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * What moves with the provident fund's base ({@code 03-advisory.md} A2.2 C8; W-238d): the employer's provident fund
 * contribution and the employee's own, where entered, each in proportion to the base at the ratio entered. The base is
 * basic, and the dearness allowance where the user states it is included (W-238j); each part of it that may move leads,
 * so basic and the allowance can both move the contributions (W-238j1), and each part that may not is held. The ratio is
 * the user's fact, not a rate the law states, so no provident fund rate sits in Java (CLAUDE.md §4). The employer's
 * share is pay the residual funds (C1); the employee's is withheld from pay itself, so the residual does not fund it.
 *
 * @param leaders each movable part of the base and its amount as entered, in {@link Movable#ORDER}; empty where nothing
 *     follows
 * @param fixed the part of the base that does not move: basic where it is not movable, and the dearness allowance
 *     entered where the base includes it and it is not movable, else nil; the base, the leaders entered plus
 *     {@code fixed}, is more than nil wherever anything follows, or there is no ratio
 * @param followers each follower and its amount as entered, in {@link Movable#ORDER}; empty where nothing follows
 */
record Following(SortedMap<Movable, Money> leaders, Money fixed, SortedMap<Movable, Money> followers) {

    Following {
        if (!followers.isEmpty() && !baseOf(leaders, fixed).isGreaterThan(Money.ZERO)) {
            String leading = String.join(" and ", leaders.keySet().stream().map(Movable::name).toList());
            throw new IllegalArgumentException(followers.keySet().iterator().next().name() + " follows " + leading
                    + " at the ratio entered, so " + leading + " must be entered above nil to move (A2.2 C8)");
        }
        leaders = Collections.unmodifiableSortedMap(copyOf(leaders));
        followers = Collections.unmodifiableSortedMap(copyOf(followers));
    }

    /** Nothing follows, so nothing leads. */
    static Following none() {
        return new Following(new TreeMap<>(Movable.ORDER), Money.ZERO, new TreeMap<>(Movable.ORDER));
    }

    boolean follows(Movable item) {
        return followers.containsKey(item);
    }

    boolean leads(Movable item) {
        return !followers.isEmpty() && leaders.containsKey(item);
    }

    /** {@code follower}'s amount when the leaders sum to {@code leadersAmount}: the entered ratio to the base kept. */
    Money amountAt(Movable follower, Money leadersAmount) {
        return followers.get(follower).scaledBy(leadersAmount.plus(fixed), baseOf(leaders, fixed));
    }

    /** Whether the residual pays for {@code follower}: an employer's contribution is pay, an employee's is withheld. */
    static boolean isFundedByResidual(Movable follower) {
        return follower instanceof Movable.Fund;
    }

    /**
     * The most a leader can reach from {@code leaderAmount} before the residual is spent: each rupee of it takes one
     * rupee and the residual-funded followers' share per rupee of the base entered — the same for every leader, since a
     * rupee of any is a rupee of the base.
     */
    Money ceiling(Money leaderAmount, Money residual) {
        Money funded = followers.entrySet().stream().filter(each -> isFundedByResidual(each.getKey()))
                .map(Map.Entry::getValue).reduce(Money.ZERO, Money::plus);
        if (!funded.isGreaterThan(Money.ZERO)) {
            return leaderAmount.plus(residual);
        }
        Money base = baseOf(leaders, fixed);
        return leaderAmount.plus(residual.scaledBy(base, base.plus(funded)));
    }

    private static Money baseOf(Map<Movable, Money> leaders, Money fixed) {
        return leaders.values().stream().reduce(fixed, Money::plus);
    }

    private static SortedMap<Movable, Money> copyOf(Map<Movable, Money> amounts) {
        SortedMap<Movable, Money> copy = new TreeMap<>(Movable.ORDER);
        copy.putAll(amounts);
        return copy;
    }
}
