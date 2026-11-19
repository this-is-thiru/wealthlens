package com.thiru.wealthlens.taxengine.input;

import static com.thiru.wealthlens.taxengine.input.InputRefusals.refusal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** What {@link LifeInsurancePolicy} refuses, one bad field at a time; each message is the record's own (W-240b). */
class LifeInsurancePolicyTest {

    private static final Money PREMIUM = Money.of("30000");
    private static final Money SUM = Money.of("1000000");
    private static final LocalDate ISSUED = LocalDate.of(2020, 4, 1);

    @Test
    void construct_whenEveryFieldIsValid_isAccepted() {
        // When
        LifeInsurancePolicy valid = new LifeInsurancePolicy(PREMIUM, SUM, ISSUED, false);

        // Then
        assertThat(valid.premiumPaid()).isEqualTo(PREMIUM);
    }

    static Stream<Arguments> refusals() {
        String needs = "A life insurance policy needs its premium, sum assured and issue date";
        String negatives = "A life insurance policy's premium and sum assured may not be negative";
        return Stream.of(
                refusal("premiumPaid null", () -> new LifeInsurancePolicy(null, SUM, ISSUED, false),
                        needs),
                refusal("actualCapitalSumAssured null", () -> new LifeInsurancePolicy(PREMIUM, null, ISSUED, false),
                        needs),
                refusal("issuedOn null", () -> new LifeInsurancePolicy(PREMIUM, SUM, null, false),
                        needs),
                refusal("premiumPaid negative", () -> new LifeInsurancePolicy(Money.of("-1"), SUM, ISSUED, false),
                        negatives),
                refusal("actualCapitalSumAssured negative", () -> new LifeInsurancePolicy(PREMIUM, Money.of("-1"), ISSUED, false),
                        negatives));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    void construct_whenOneFieldIsBad_isRefusedNamingIt(String badField, ThrowingCallable construct, String message) {
        // When / Then
        assertThatThrownBy(construct).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }
}
