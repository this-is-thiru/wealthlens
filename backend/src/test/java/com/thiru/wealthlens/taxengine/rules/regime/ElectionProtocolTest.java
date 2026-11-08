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

/** What {@link ElectionProtocol} refuses, one part made bad at a time; each message is the record's own (W-240c). */
class ElectionProtocolTest {

    private static final ElectionProtocol VALID = RegimeFixtures.regimeWithBehaviour(null).election();

    @Test
    void construct_whenTheFormAndRoutesAreNull_readsThemAsNone() throws Throwable {
        // When — null is how a pack omits them (ElectionProtocol.java:55, :58)
        ElectionProtocol omitted = withComponent(withComponent(VALID, "form", null), "vehicleWhen", null);

        // Then
        assertThat(omitted.form()).isEmpty();
        assertThat(omitted.vehicleWhen()).isEmpty();
    }

    static Stream<Arguments> refusals() throws Throwable {
        String kindAndVehicle = "An election protocol needs both a kind and a vehicle";
        return Stream.of(
                refusal("kind null", () -> withComponent(VALID, "kind", null),
                        kindAndVehicle),
                refusal("vehicle null", () -> withComponent(VALID, "vehicle", null),
                        kindAndVehicle),
                refusal("deadline null", () -> withComponent(VALID, "deadline", null),
                        "An election protocol must state a deadline"),
                refusal("binding null", () -> withComponent(VALID, "binding", null),
                        "An election protocol must state its binding"),
                refusal("provenance null", () -> withComponent(VALID, "provenance", null),
                        "An election protocol must say where it was read from"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    void construct_whenOneFieldIsBad_isRefusedNamingIt(String badField, ThrowingCallable construct, String message) {
        // When / Then
        assertThatThrownBy(construct).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }
}
