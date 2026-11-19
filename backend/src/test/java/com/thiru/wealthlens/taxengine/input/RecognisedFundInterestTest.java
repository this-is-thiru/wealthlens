package com.thiru.wealthlens.taxengine.input;

import static com.thiru.wealthlens.taxengine.input.InputRefusals.refusal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** What {@link RecognisedFundInterest} refuses, one bad field at a time; each message is the record's own (W-240b). */
class RecognisedFundInterestTest {

    private static final Money CREDITED = Money.of("90000");
    private static final Rate ALLOWED = Rate.percent("9.5");

    @Test
    void construct_whenEveryFieldIsValid_isAccepted() {
        // When
        RecognisedFundInterest valid = new RecognisedFundInterest(0, CREDITED, ALLOWED);

        // Then
        assertThat(valid.interestCredited()).isEqualTo(CREDITED);
    }

    static Stream<Arguments> refusals() {
        String needs = "A fund's interest needs the amount credited and the rate allowed";
        String negatives = "A fund's interest position, amount and rate may not be negative";
        return Stream.of(
                refusal("interestCredited null", () -> new RecognisedFundInterest(0, null, ALLOWED),
                        needs),
                refusal("rateAllowed null", () -> new RecognisedFundInterest(0, CREDITED, null),
                        needs),
                refusal("employment negative", () -> new RecognisedFundInterest(-1, CREDITED, ALLOWED),
                        negatives),
                refusal("interestCredited negative", () -> new RecognisedFundInterest(0, Money.of("-1"), ALLOWED),
                        negatives),
                refusal("rateAllowed negative", () -> new RecognisedFundInterest(0, CREDITED, Rate.percent("-1")),
                        negatives));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    void construct_whenOneFieldIsBad_isRefusedNamingIt(String badField, ThrowingCallable construct, String message) {
        // When / Then
        assertThatThrownBy(construct).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }
}
