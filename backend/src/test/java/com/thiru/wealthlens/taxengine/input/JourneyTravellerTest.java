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

/** What {@link JourneyTraveller} refuses, one bad field at a time; each message is the record's own (W-240b). */
class JourneyTravellerTest {

    private static final Money AMOUNT = Money.of("12000");

    @Test
    void construct_whenEveryFieldIsValid_isAccepted() {
        // When
        JourneyTraveller valid = new JourneyTraveller(Optional.empty(), AMOUNT, Optional.of(AMOUNT));

        // Then
        assertThat(valid.amountIncurred()).isEqualTo(AMOUNT);
    }

    static Stream<Arguments> refusals() {
        String needs = "A traveller needs an amount";
        String negatives = "A traveller's amount incurred and fare may not be negative";
        return Stream.of(
                refusal("familyMember null", () -> new JourneyTraveller(null, AMOUNT, Optional.empty()),
                        needs),
                refusal("amountIncurred null", () -> new JourneyTraveller(Optional.empty(), null, Optional.empty()),
                        needs),
                refusal("fareAdmissible null", () -> new JourneyTraveller(Optional.empty(), AMOUNT, null),
                        needs),
                refusal("family position negative", () -> new JourneyTraveller(Optional.of(-1), AMOUNT, Optional.empty()),
                        "A traveller's family position is none or more"),
                refusal("amountIncurred negative", () -> new JourneyTraveller(Optional.empty(), Money.of("-1"), Optional.empty()),
                        negatives),
                refusal("fare negative", () -> new JourneyTraveller(Optional.empty(), AMOUNT, Optional.of(Money.of("-1"))),
                        negatives));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    void construct_whenOneFieldIsBad_isRefusedNamingIt(String badField, ThrowingCallable construct, String message) {
        // When / Then
        assertThatThrownBy(construct).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }
}
