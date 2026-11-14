package com.thiru.wealthlens.taxengine.rules.catalogue;

import com.thiru.wealthlens.taxengine.rules.temporal.Provision;

/**
 * Where the law puts a claim on its channel, in each Act, as a section catalogue entry states its provisions: a person
 * told "you can still claim this in your return" deserves the provision that says so for their year.
 */
public record ClaimBasis(Provision provision1961, Provision provision2025) {

    public ClaimBasis {
        if (provision1961 == null || provision2025 == null) {
            throw new IllegalArgumentException("A claim's basis must state its provision in both Acts");
        }
    }
}
