package com.thiru.wealthlens.taxengine.input;

import static com.thiru.wealthlens.taxengine.input.InputRefusals.refusal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** What {@link HealthPayment} refuses, one bad field at a time; each message is the record's own (W-240b). */
class HealthPaymentTest {

    private static final HealthPaymentKind PREMIUM = HealthPaymentKind.INSURANCE_PREMIUM;
    private static final Money AMOUNT = Money.of("25000");

    @Test
    void construct_whenEveryFieldIsValid_isAccepted() {
        // When
        HealthPayment valid = new HealthPayment(PREMIUM, true, List.of(0), AMOUNT, false, 1);

        // Then
        assertThat(valid.familyMembers()).containsExactly(0);
    }

    static Stream<Arguments> refusals() {
        String needs = "A health payment needs its kind, the people it covers and its amount";
        String valid = "A health payment's amount, positions and years must be valid";
        return Stream.of(
                refusal("kind null", () -> new HealthPayment(null, true, List.of(), AMOUNT, false, 1),
                        needs),
                refusal("familyMembers null", () -> new HealthPayment(PREMIUM, true, null, AMOUNT, false, 1),
                        needs),
                refusal("amount null", () -> new HealthPayment(PREMIUM, true, List.of(), null, false, 1),
                        needs),
                refusal("covers nobody", () -> new HealthPayment(PREMIUM, false, List.of(), AMOUNT, false, 1),
                        "A health payment must cover someone"),
                refusal("amount negative", () -> new HealthPayment(PREMIUM, true, List.of(), Money.of("-1"), false, 1),
                        valid),
                refusal("no relevant year", () -> new HealthPayment(PREMIUM, true, List.of(), AMOUNT, false, 0),
                        valid),
                refusal("family position negative", () -> new HealthPayment(PREMIUM, true, List.of(-1), AMOUNT, false, 1),
                        valid));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    void construct_whenOneFieldIsBad_isRefusedNamingIt(String badField, ThrowingCallable construct, String message) {
        // When / Then
        assertThatThrownBy(construct).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }
}
