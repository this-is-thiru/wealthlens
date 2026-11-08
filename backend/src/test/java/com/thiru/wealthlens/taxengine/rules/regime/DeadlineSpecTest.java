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

/** What {@link DeadlineSpec.DueDateUnder} refuses (W-240c). */
class DeadlineSpecTest {

    @Test
    void construct_whenEveryFieldIsValid_isAccepted() {
        // When
        DeadlineSpec.DueDateUnder valid = new DeadlineSpec.DueDateUnder("s.263(1)");

        // Then
        assertThat(valid.section()).isEqualTo("s.263(1)");
    }

    static Stream<Arguments> refusals() {
        return Stream.of(
                refusal("section null", () -> new DeadlineSpec.DueDateUnder(null),
                        "DueDateUnder must name the provision it reads"),
                refusal("section blank", () -> new DeadlineSpec.DueDateUnder(" "),
                        "DueDateUnder must name the provision it reads"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    void construct_whenOneFieldIsBad_isRefusedNamingIt(String badField, ThrowingCallable construct, String message) {
        // When / Then
        assertThatThrownBy(construct).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }
}
