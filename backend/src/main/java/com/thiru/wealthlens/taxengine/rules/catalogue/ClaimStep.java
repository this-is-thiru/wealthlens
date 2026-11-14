package com.thiru.wealthlens.taxengine.rules.catalogue;

import java.util.Optional;

/**
 * One thing to do to make a claim ({@code DR 22} C3).
 *
 * @param order its place among the entry's steps, from 1
 * @param instruction what to do, as an instruction
 * @param actor who does it
 * @param by when it has to be done by
 * @param produces what doing it leaves the person holding, where it leaves something
 * @param warnsOfLateReturnBar whether the step tells the person a late return loses the deduction (I24)
 */
public record ClaimStep(int order, String instruction, ClaimActor actor, ClaimDeadline by, Optional<String> produces,
        boolean warnsOfLateReturnBar) {

    public ClaimStep {
        if (order < 1) {
            throw new IllegalArgumentException("A claim step's order starts at 1, but was " + order);
        }
        if (instruction == null || instruction.isBlank() || actor == null || by == null || produces == null) {
            throw new IllegalArgumentException("A claim step needs an instruction, an actor, a deadline (NONE for none)"
                    + " and produces (Optional.empty() for nothing)");
        }
    }
}
