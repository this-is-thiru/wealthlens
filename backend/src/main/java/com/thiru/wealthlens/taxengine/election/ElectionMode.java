package com.thiru.wealthlens.taxengine.election;

/**
 * How an election's choice was made ({@code DR 15} C2).
 *
 * <p>{@code DR 15} C2 also names {@code STATUTORY_DEFAULT}, for a choice the law fixes; no built election has one, so
 * it is not declared (D-221 ruled the set-off order the assessee's).
 */
public enum ElectionMode {

    /** The assessee stated the choice; it is applied as stated. */
    USER_SPECIFIED,

    /** The assessee stated none; every choice is priced and the cheapest taken. */
    AUTO_OPTIMISE
}
