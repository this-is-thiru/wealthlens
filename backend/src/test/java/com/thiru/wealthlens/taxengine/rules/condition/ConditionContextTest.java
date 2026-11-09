package com.thiru.wealthlens.taxengine.rules.condition;

import static com.thiru.wealthlens.taxengine.rules.RuleRefusals.refusal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** What {@link ConditionContext} refuses, one missing part at a time; each message is the record's own (W-240c). */
class ConditionContextTest {

    private static final PersonType INDIVIDUAL = PersonType.INDIVIDUAL;

    @Test
    void construct_whenEveryPartIsGiven_isAccepted() {
        // When
        ConditionContext valid = new ConditionContext(ResidentialStatus.RESIDENT, INDIVIDUAL, Map.of(), Map.of(), Map.of(), Set.of(), Set.of(),
                Set.of(), ItemFacts.NONE, Declarations.NONE, Optional.empty());

        // Then
        assertThat(valid.personType()).isEqualTo(INDIVIDUAL);
    }

    static Stream<Arguments> refusals() {
        String maps = "ConditionContext maps may not be null";
        String collections = "ConditionContext collections may not be null";
        return Stream.of(
                refusal("ages null", () -> new ConditionContext(ResidentialStatus.RESIDENT, INDIVIDUAL, null, Map.of(), Map.of(), Set.of(),
                        Set.of(), Set.of(), ItemFacts.NONE, Declarations.NONE, Optional.empty()), maps),
                refusal("disabilities null", () -> new ConditionContext(ResidentialStatus.RESIDENT, INDIVIDUAL, Map.of(), null, Map.of(),
                        Set.of(), Set.of(), Set.of(), ItemFacts.NONE, Declarations.NONE, Optional.empty()), maps),
                refusal("nodeValues null", () -> new ConditionContext(ResidentialStatus.RESIDENT, INDIVIDUAL, Map.of(), Map.of(), null,
                        Set.of(), Set.of(), Set.of(), ItemFacts.NONE, Declarations.NONE, Optional.empty()), collections),
                refusal("headsWithIncome null", () -> new ConditionContext(ResidentialStatus.RESIDENT, INDIVIDUAL, Map.of(), Map.of(),
                        Map.of(), null, Set.of(), Set.of(), ItemFacts.NONE, Declarations.NONE, Optional.empty()), collections),
                refusal("componentsPresent null", () -> new ConditionContext(ResidentialStatus.RESIDENT, INDIVIDUAL, Map.of(), Map.of(),
                        Map.of(), Set.of(), null, Set.of(), ItemFacts.NONE, Declarations.NONE, Optional.empty()), collections),
                refusal("evidenceRecorded null", () -> new ConditionContext(ResidentialStatus.RESIDENT, INDIVIDUAL, Map.of(), Map.of(),
                        Map.of(), Set.of(), Set.of(), null, ItemFacts.NONE, Declarations.NONE, Optional.empty()), collections),
                refusal("item null", () -> new ConditionContext(ResidentialStatus.RESIDENT, INDIVIDUAL, Map.of(), Map.of(), Map.of(),
                        Set.of(), Set.of(), Set.of(), null, Declarations.NONE, Optional.empty()), collections),
                refusal("declarations null", () -> new ConditionContext(ResidentialStatus.RESIDENT, INDIVIDUAL, Map.of(), Map.of(), Map.of(),
                        Set.of(), Set.of(), Set.of(), ItemFacts.NONE, null, Optional.empty()), collections),
                refusal("residence null", () -> new ConditionContext(null, INDIVIDUAL, Map.of(), Map.of(), Map.of(),
                        Set.of(), Set.of(), Set.of(), ItemFacts.NONE, Declarations.NONE, Optional.empty()),
                        "must state the residential status"),
                refusal("ownedHouseProperties null", () -> new ConditionContext(ResidentialStatus.RESIDENT, INDIVIDUAL,
                        Map.of(), Map.of(), Map.of(), Set.of(), Set.of(), Set.of(), ItemFacts.NONE, Declarations.NONE,
                        null), "ownedHouseProperties may not be null"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    void construct_whenOnePartIsMissing_isRefusedNamingIt(String badField, ThrowingCallable construct, String message) {
        // When / Then
        assertThatThrownBy(construct).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }
}
