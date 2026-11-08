package com.thiru.wealthlens.taxengine.rules.regime;

import static com.thiru.wealthlens.taxengine.rules.RuleRefusals.refusal;
import static com.thiru.wealthlens.taxengine.rules.RuleRefusals.withComponent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** What {@link BindingSpec} refuses (W-240c). */
class BindingSpecTest {

    @Test
    void construct_whenEveryFieldIsValid_isAccepted() {
        // When
        BindingSpec valid = BindingSpec.free();

        // Then
        assertThat(valid.afterWithdrawal()).isNotNull();
    }

    static Stream<Arguments> refusals() throws Throwable {
        return Stream.of(
                refusal("afterWithdrawal null", () -> withComponent(BindingSpec.free(), "afterWithdrawal", null),
                        "A binding must say what happens after withdrawal"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    void construct_whenOneFieldIsBad_isRefusedNamingIt(String badField, ThrowingCallable construct, String message) {
        // When / Then
        assertThatThrownBy(construct).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }
}
