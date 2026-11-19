package com.thiru.wealthlens.taxengine.rules.regime;

import static com.thiru.wealthlens.taxengine.rules.RuleRefusals.refusal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** What {@link InvalidationPolicy.ProspectiveOnly} refuses (W-240c). */
class InvalidationPolicyTest {

    @Test
    void construct_whenEveryFieldIsValid_isAccepted() {
        // When
        InvalidationPolicy.ProspectiveOnly valid = new InvalidationPolicy.ProspectiveOnly(new RuleId("RATE-SLAB-NEW-TAX-REGIME"));

        // Then
        assertThat(valid.citedRule()).isPresent();
    }

    static Stream<Arguments> refusals() {
        return Stream.of(
                refusal("basis null", () -> new InvalidationPolicy.ProspectiveOnly(null),
                        "ProspectiveOnly must name its basis"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    void construct_whenOneFieldIsBad_isRefusedNamingIt(String badField, ThrowingCallable construct, String message) {
        // When / Then
        assertThatThrownBy(construct).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }
}
