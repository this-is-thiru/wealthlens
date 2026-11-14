package com.thiru.wealthlens.taxengine.election;

import java.util.List;

/**
 * What an election resolved to, and every alternative priced on the way ({@code DR 15} C2).
 *
 * <p>The alternatives are kept, not only the winner: a person told which choice was made for them deserves to see the
 * others and their cost, and an auditor to see that the choice was made on price. Not a {@code NodeValue} yet — the
 * resolver runs outside the graph; it becomes one when a node first produces it (W-083; D-220).
 *
 * @param chosen the choice applied
 * @param mode whether the assessee stated it or it was the cheapest
 * @param alternatives each choice computed and its net payable, in the order computed; one, the stated choice, where
 *     the assessee stated it
 * @param <T> what is chosen
 */
public record ElectionOutcome<T>(T chosen, ElectionMode mode, List<PricedAlternative<T>> alternatives) {

    public ElectionOutcome {
        if (chosen == null || mode == null || alternatives == null || alternatives.isEmpty()) {
            throw new IllegalArgumentException("An election outcome needs its choice, its mode and the alternatives"
                    + " priced");
        }
        alternatives = List.copyOf(alternatives);
        if (alternatives.stream().noneMatch(alternative -> alternative.choice().equals(chosen))) {
            throw new IllegalArgumentException("The choice " + chosen + " is not among the alternatives priced");
        }
        if (mode == ElectionMode.USER_SPECIFIED && alternatives.size() != 1) {
            throw new IllegalArgumentException("A stated choice is computed alone, but " + alternatives.size()
                    + " alternatives were priced");
        }
    }
}
