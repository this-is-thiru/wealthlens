package com.thiru.wealthlens.taxengine.rules.regime;

import static com.thiru.wealthlens.taxengine.rules.RuleRefusals.refusal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** What {@link VehicleRoute} refuses and how it reads an omitted part (W-240c). */
class VehicleRouteTest {

    @Test
    void construct_whenTheConditionsAndFormAreNull_readsThemAsNone() {
        // When — null is how a pack omits them (VehicleRoute.java:29, :30)
        VehicleRoute route = new VehicleRoute(null, ElectionVehicle.IN_THE_RETURN, null);

        // Then
        assertThat(route.appliesWhen()).isEmpty();
        assertThat(route.form()).isEmpty();
    }

    static Stream<Arguments> refusals() {
        return Stream.of(
                refusal("vehicle null", () -> new VehicleRoute(List.of(), null, Optional.empty()),
                        "A vehicle route must name its vehicle"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    void construct_whenOneFieldIsBad_isRefusedNamingIt(String badField, ThrowingCallable construct, String message) {
        // When / Then
        assertThatThrownBy(construct).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }
}
