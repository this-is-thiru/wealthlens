package com.thiru.wealthlens.taxengine.rules.regime;

import static com.thiru.wealthlens.taxengine.rules.RuleRefusals.refusal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Resolution of the tier-3 escape hatch.
 *
 * <p>The synthetic behaviours here exist only in this file. L5 recommends shipping with none, and
 * an unexercised guard is barely a guard — so the registry is proved against a behaviour that never
 * reaches production source.
 */
class RegimeBehaviourRegistryTest {

    static final BehaviourId NOVEL = new BehaviourId("NOVEL_ELECTION");

    /** A behaviour that exists only for this test. */
    record SyntheticBehaviour(BehaviourId id) implements RegimeBehaviour {}

    @Nested
    @DisplayName("resolution")
    class Resolution {

        @Test
        void aRegimeNamingNoBehaviourGetsTheDataDrivenDefault() {
            // So callers never branch on absence
            RegimeBehaviourRegistry registry = RegimeBehaviourRegistry.empty();

            assertThat(registry.forRegime(RegimeFixtures.regimeWithBehaviour(null)))
                    .isSameAs(DataDrivenRegimeBehaviour.instance());
        }

        @Test
        void aRegimeNamingABehaviourGetsIt() {
            RegimeBehaviour behaviour = new SyntheticBehaviour(NOVEL);
            RegimeBehaviourRegistry registry = RegimeBehaviourRegistry.of(List.of(behaviour));

            assertThat(registry.forRegime(RegimeFixtures.regimeWithBehaviour(NOVEL))).isSameAs(behaviour);
        }

        @Test
        void resolvingAnUnregisteredBehaviourBlamesTheValidator() {
            // The pack should never have loaded; the message says so rather than failing blankly
            assertThatThrownBy(() ->
                            RegimeBehaviourRegistry.empty()
                                    .forRegime(RegimeFixtures.regimeWithBehaviour(NOVEL)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("invariant I9");
        }
    }

    @Nested
    @DisplayName("registry integrity")
    class Integrity {

        @Test
        void twoBehavioursClaimingOneIdAreRejected() {
            assertThatThrownBy(() ->
                            RegimeBehaviourRegistry.of(
                                    List.of(new SyntheticBehaviour(NOVEL), new SyntheticBehaviour(NOVEL))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("both claim");
        }

        @Test
        void aBehaviourFiledUnderTheWrongIdIsRejected() {
            assertThatThrownBy(() ->
                            new RegimeBehaviourRegistry(
                                    Map.of(new BehaviourId("SOMETHING_ELSE"), new SyntheticBehaviour(NOVEL))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("calls itself");
        }

        @Test
        void theDefaultReservesItsOwnId() {
            assertThat(DataDrivenRegimeBehaviour.instance().id()).isEqualTo(DataDrivenRegimeBehaviour.ID);
        }

        @Test
        void theEmptyRegistryIsTheShippingState() {
            // L5: ship with zero implementations other than the default
            assertThat(RegimeBehaviourRegistry.empty().registeredIds()).isEmpty();
        }
    }

    /** A missing registry is refused (W-240c). */
    static Stream<Arguments> refusals() {
        return Stream.of(refusal("behaviours null", () -> new RegimeBehaviourRegistry(null),
                "A behaviour registry may not be null; pass an empty one"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    void construct_whenOneFieldIsBad_isRefusedNamingIt(String badField, ThrowingCallable construct, String message) {
        // When / Then
        assertThatThrownBy(construct).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }
}
