package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.condition.PersonType;

/**
 * A Hindu undivided family. It has no date of birth and no disability; its karta and members arrive
 * with the first rule that reads them ({@code 00-BRIEF.md} §8, rule B2), so today it carries only
 * residence.
 */
public record Huf(ResidentialStatus residence) implements Assessee {

    public Huf {
        if (residence == null) {
            throw new IllegalArgumentException("A Huf must state a residential status");
        }
    }

    @Override
    public PersonType type() {
        return PersonType.HUF;
    }
}
