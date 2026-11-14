package com.thiru.wealthlens.taxengine.rules.catalogue;

/**
 * Someone a claim needs something from ({@code DR 22} C3).
 *
 * @param whom who to ask
 * @param askFor what to ask them for
 */
public record ClaimRequest(ClaimActor whom, String askFor) {

    public ClaimRequest {
        if (whom == null || askFor == null || askFor.isBlank()) {
            throw new IllegalArgumentException("A claim request names whom to ask and what for");
        }
    }
}
