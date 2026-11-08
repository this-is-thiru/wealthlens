package com.thiru.wealthlens.taxengine.input;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.params.provider.Arguments;

/**
 * One case of an input record's refusal, for a parameterised test: the field made bad, the construction with only that
 * field bad, and the fragment of the record's own message it must carry (W-240a).
 */
final class InputRefusals {

    private InputRefusals() {
    }

    static Arguments refusal(String badField, ThrowingCallable construct, String message) {
        return Arguments.of(badField, construct, message);
    }
}
