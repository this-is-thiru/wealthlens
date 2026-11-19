package com.thiru.wealthlens.taxengine.input;

import static com.thiru.wealthlens.taxengine.input.InputRefusals.refusal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Optional;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** What {@link Furniture} refuses, one bad field at a time; each message is the record's own (W-240b). */
class FurnitureTest {

    private static final Optional<Money> COST = Optional.of(Money.of("80000"));

    @Test
    void construct_whenEveryFieldIsValid_isAccepted() {
        // When
        Furniture valid = new Furniture(COST, Optional.empty(), Money.ZERO);

        // Then
        assertThat(valid.cost()).isEqualTo(COST);
    }

    static Stream<Arguments> refusals() {
        String needs = "Furniture needs its cost or hire charges, and the employee's charges";
        String negatives = "Furniture's amounts may not be negative";
        return Stream.of(
                refusal("cost null", () -> new Furniture(null, Optional.empty(), Money.ZERO),
                        needs),
                refusal("hireCharges null", () -> new Furniture(COST, null, Money.ZERO),
                        needs),
                refusal("employeeCharges null", () -> new Furniture(COST, Optional.empty(), null),
                        needs),
                refusal("employeeCharges negative", () -> new Furniture(COST, Optional.empty(), Money.of("-1")),
                        negatives),
                refusal("cost negative", () -> new Furniture(Optional.of(Money.of("-1")), Optional.empty(), Money.ZERO),
                        negatives),
                refusal("hire charges negative", () -> new Furniture(Optional.empty(), Optional.of(Money.of("-1")), Money.ZERO),
                        negatives));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    void construct_whenOneFieldIsBad_isRefusedNamingIt(String badField, ThrowingCallable construct, String message) {
        // When / Then
        assertThatThrownBy(construct).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }
}
