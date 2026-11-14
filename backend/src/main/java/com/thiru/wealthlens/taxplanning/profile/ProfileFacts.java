package com.thiru.wealthlens.taxplanning.profile;

import com.thiru.wealthlens.shared.exception.BadRequestException;

/** The checks a profile's requests share, each one sentence and none repeating an identifier. */
final class ProfileFacts {

    private ProfileFacts() {
    }

    static void requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("A name is required");
        }
    }

    static void requirePanIfGiven(String pan) {
        if (pan != null) {
            new Pan(pan);
        }
    }

    static void require(Object fact, String sentence) {
        if (fact == null) {
            throw new BadRequestException(sentence);
        }
    }
}
