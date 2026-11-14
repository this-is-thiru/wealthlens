package com.thiru.wealthlens.taxengine.rules.catalogue;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * What a person does to make a claim ({@code DR 22} C3): the steps in order, who to ask, what to keep and what goes
 * wrong.
 *
 * @param appliesTo the cases the entry covers, where it does not cover every case of its rule
 * @param steps what to do, numbered from 1 without a gap
 * @param askWhom who the claim needs something from
 * @param evidence the documents it rests on
 * @param mistakes what people commonly get wrong
 */
public record ClaimGuidance(Optional<String> appliesTo, List<ClaimStep> steps, List<ClaimRequest> askWhom,
        List<ClaimEvidence> evidence, List<String> mistakes) {

    public ClaimGuidance {
        if (appliesTo == null || steps == null || askWhom == null || evidence == null || mistakes == null) {
            throw new IllegalArgumentException("Claim guidance needs appliesTo (Optional.empty() for every case), steps,"
                    + " askWhom, evidence and mistakes (empty lists for none)");
        }
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("A claim needs at least one step");
        }
        List<Integer> orders = steps.stream().map(ClaimStep::order).toList();
        if (!orders.equals(IntStream.rangeClosed(1, steps.size()).boxed().toList())) {
            throw new IllegalArgumentException("Claim steps are numbered from 1 without a gap, but were " + orders);
        }
        if (mistakes.stream().anyMatch(mistake -> mistake == null || mistake.isBlank())) {
            throw new IllegalArgumentException("A common mistake cannot be blank");
        }
        steps = List.copyOf(steps);
        askWhom = List.copyOf(askWhom);
        evidence = List.copyOf(evidence);
        mistakes = List.copyOf(mistakes);
    }
}
