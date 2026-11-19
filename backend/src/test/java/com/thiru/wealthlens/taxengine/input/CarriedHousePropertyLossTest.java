package com.thiru.wealthlens.taxengine.input;

import static com.thiru.wealthlens.taxengine.input.InputRefusals.refusal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** What {@link CarriedHousePropertyLoss} refuses, one bad field at a time; each message is the record's own (W-240b). */
class CarriedHousePropertyLossTest {

    private static final Money LOSS = Money.of("200000");

    @Test
    void construct_whenEveryFieldIsValid_isAccepted() {
        // When
        CarriedHousePropertyLoss valid = new CarriedHousePropertyLoss(LOSS, Money.ZERO, Money.ZERO);

        // Then
        assertThat(valid.loss()).isEqualTo(LOSS);
    }

    static Stream<Arguments> refusals() {
        String needs = "A carried house-property loss needs every figure; enter 0 where nil";
        String negatives = "A carried house-property loss's figures may not be negative";
        return Stream.of(
                refusal("loss null", () -> new CarriedHousePropertyLoss(null, Money.ZERO, Money.ZERO),
                        needs),
                refusal("attributable null", () -> new CarriedHousePropertyLoss(LOSS, null, Money.ZERO),
                        needs),
                refusal("absorbedSince null", () -> new CarriedHousePropertyLoss(LOSS, Money.ZERO, null),
                        needs),
                refusal("loss negative", () -> new CarriedHousePropertyLoss(Money.of("-1"), Money.ZERO, Money.ZERO),
                        negatives),
                refusal("attributable negative", () -> new CarriedHousePropertyLoss(LOSS, Money.of("-1"), Money.ZERO),
                        negatives),
                refusal("absorbedSince negative", () -> new CarriedHousePropertyLoss(LOSS, Money.ZERO, Money.of("-1")),
                        negatives));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    void construct_whenOneFieldIsBad_isRefusedNamingIt(String badField, ThrowingCallable construct, String message) {
        // When / Then
        assertThatThrownBy(construct).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }
}
