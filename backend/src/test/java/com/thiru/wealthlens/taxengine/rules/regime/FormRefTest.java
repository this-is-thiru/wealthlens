package com.thiru.wealthlens.taxengine.rules.regime;

import static com.thiru.wealthlens.taxengine.rules.RuleRefusals.refusal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** What {@link FormRef} refuses, one part made bad at a time (W-240c). */
class FormRefTest {

    @Test
    void construct_whenEveryFieldIsValid_isAccepted() {
        // When
        FormRef valid = new FormRef("10-IEA", "Option for the old regime");

        // Then
        assertThat(valid.number()).isEqualTo("10-IEA");
    }

    static Stream<Arguments> refusals() {
        String number = "A form reference must carry its number";
        String purpose = "must say what it is for";
        return Stream.of(
                refusal("number null", () -> new FormRef(null, "Option"),
                        number),
                refusal("number blank", () -> new FormRef(" ", "Option"),
                        number),
                refusal("displayName null", () -> new FormRef("10-IEA", null),
                        purpose),
                refusal("displayName blank", () -> new FormRef("10-IEA", " "),
                        purpose));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    void construct_whenOneFieldIsBad_isRefusedNamingIt(String badField, ThrowingCallable construct, String message) {
        // When / Then
        assertThatThrownBy(construct).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }
}
