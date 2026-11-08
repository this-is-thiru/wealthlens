package com.thiru.wealthlens.taxengine.optimise;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * What moves with basic pay ({@code 03-advisory.md} A2.2 C8; W-238d): the employer's provident fund contribution and the
 * employee's own, where entered, each in proportion to its base at the ratio entered: basic, or basic and the
 * dearness allowance where the user states the base includes it (W-238j), the allowance held. The ratio is the user's fact, not a
 * rate the law states, so no provident fund rate sits in Java (CLAUDE.md §4). The employer's share is pay the residual
 * funds (C1); the employee's is withheld from basic itself, so the residual does not fund it.
 *
 * @param leader the component the others follow
 * @param leaderEntered its amount as entered
 * @param fixed the part of the followers' base that does not move: the dearness allowance entered where the base
 *     includes it (W-238j), else nil; the base, {@code leaderEntered + fixed}, is more than nil wherever anything
 *     follows, or there is no ratio
 * @param followers each follower and its amount as entered, in {@link Movable#ORDER}; empty where nothing follows
 */
record Following(Movable.Pay leader, Money leaderEntered, Money fixed, SortedMap<Movable, Money> followers) {

    Following {
        if (!followers.isEmpty() && !leaderEntered.plus(fixed).isGreaterThan(Money.ZERO)) {
            throw new IllegalArgumentException(followers.keySet().iterator().next().name() + " follows "
                    + leader.name() + " at the ratio entered, so " + leader.name()
                    + " must be entered above nil to move (A2.2 C8)");
        }
        SortedMap<Movable, Money> copy = new TreeMap<>(Movable.ORDER);
        copy.putAll(followers);
        followers = Collections.unmodifiableSortedMap(copy);
    }

    boolean follows(Movable item) {
        return followers.containsKey(item);
    }

    /** {@code follower}'s amount when the leader is at {@code leaderAmount}: the entered ratio to the base kept. */
    Money amountAt(Movable follower, Money leaderAmount) {
        return followers.get(follower).scaledBy(leaderAmount.plus(fixed), leaderEntered.plus(fixed));
    }

    /** Whether the residual pays for {@code follower}: an employer's contribution is pay, an employee's is withheld. */
    static boolean isFundedByResidual(Movable follower) {
        return follower instanceof Movable.Fund;
    }

    /**
     * The most the leader can reach from {@code leaderAmount} before the residual is spent: each rupee of it takes one
     * rupee and the residual-funded followers' share per rupee of the base entered.
     */
    Money ceiling(Money leaderAmount, Money residual) {
        Money funded = followers.entrySet().stream().filter(each -> isFundedByResidual(each.getKey()))
                .map(Map.Entry::getValue).reduce(Money.ZERO, Money::plus);
        if (!funded.isGreaterThan(Money.ZERO)) {
            return leaderAmount.plus(residual);
        }
        Money base = leaderEntered.plus(fixed);
        return leaderAmount.plus(residual.scaledBy(base, base.plus(funded)));
    }
}
