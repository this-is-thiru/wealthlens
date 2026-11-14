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

/** What the regime vocabulary's four identifiers refuse: a missing value, or one outside the pattern (W-240c). */
class RegimeIdsTest {

    @Test
    void construct_whenUpperCaseWithUnderscores_isAccepted() {
        // When / Then
        assertThat(new ElectionRoute("NO_BUSINESS_OR_PROFESSION").value()).isEqualTo("NO_BUSINESS_OR_PROFESSION");
        assertThat(new ConditionSetId("ELIG-S205-2").value()).isEqualTo("ELIG-S205-2");
        assertThat(new BehaviourId("OLD_REGIME_RESTORE").value()).isEqualTo("OLD_REGIME_RESTORE");
        assertThat(new DisallowanceSetId("DIS-S202-2A").value()).isEqualTo("DIS-S202-2A");
    }

    static Stream<Arguments> refusals() {
        return Stream.of(
                refusal("ElectionRoute null", () -> new ElectionRoute(null),
                        "An ElectionRoute must be upper-case with underscores"),
                refusal("ElectionRoute lower case", () -> new ElectionRoute("lower"),
                        "An ElectionRoute must be upper-case with underscores"),
                refusal("ConditionSetId null", () -> new ConditionSetId(null),
                        "A ConditionSetId must be upper-case with hyphens or underscores"),
                refusal("ConditionSetId lower case", () -> new ConditionSetId("lower"),
                        "A ConditionSetId must be upper-case with hyphens or underscores"),
                refusal("BehaviourId null", () -> new BehaviourId(null),
                        "A BehaviourId must be upper-case with underscores"),
                refusal("BehaviourId lower case", () -> new BehaviourId("lower"),
                        "A BehaviourId must be upper-case with underscores"),
                refusal("DisallowanceSetId null", () -> new DisallowanceSetId(null),
                        "A DisallowanceSetId must be upper-case with hyphens or underscores"),
                refusal("DisallowanceSetId lower case", () -> new DisallowanceSetId("lower"),
                        "A DisallowanceSetId must be upper-case with hyphens or underscores"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    void construct_whenOneFieldIsBad_isRefusedNamingIt(String badField, ThrowingCallable construct, String message) {
        // When / Then
        assertThatThrownBy(construct).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }
}
