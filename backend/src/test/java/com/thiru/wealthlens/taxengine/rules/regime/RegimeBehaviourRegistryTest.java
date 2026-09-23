package com.thiru.wealthlens.taxengine.rules.regime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Resolution of the tier-3 escape hatch.
 *
 * <p>The synthetic behaviours here exist only in this file. L5 recommends shipping with none, and
 * an unexercised guard is barely a guard — so the registry is proved against a behaviour that never
 * reaches production source.
 */
class RegimeBehaviourRegistryTest {

    static final BehaviourId NOVEL = new BehaviourId("S999_NOVEL_ELECTION");

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
}
