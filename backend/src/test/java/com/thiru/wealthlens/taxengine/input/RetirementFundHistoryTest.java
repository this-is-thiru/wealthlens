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

/** What {@link RetirementFundHistory} refuses, one bad field at a time; each message is the record's own (W-240b). */
class RetirementFundHistoryTest {

    private static final Money FIGURE = Money.of("1000");

    @Test
    void construct_whenEveryFieldIsValid_isAccepted() {
        // When
        RetirementFundHistory valid = new RetirementFundHistory(FIGURE, FIGURE, FIGURE, FIGURE, FIGURE);

        // Then
        assertThat(valid.closingBalance()).isEqualTo(FIGURE);
    }

    static Stream<Arguments> refusals() {
        String needs = "A retirement fund history needs every figure; enter 0 where nil";
        String negatives = "A retirement fund history's figures may not be negative";
        return Stream.of(
                refusal("earlierExcessContributions null", () -> new RetirementFundHistory(null, FIGURE, FIGURE, FIGURE, FIGURE),
                        needs),
                refusal("earlierTaxedAccretions null", () -> new RetirementFundHistory(FIGURE, null, FIGURE, FIGURE, FIGURE),
                        needs),
                refusal("incomeAccrued null", () -> new RetirementFundHistory(FIGURE, FIGURE, null, FIGURE, FIGURE),
                        needs),
                refusal("openingBalance null", () -> new RetirementFundHistory(FIGURE, FIGURE, FIGURE, null, FIGURE),
                        needs),
                refusal("closingBalance null", () -> new RetirementFundHistory(FIGURE, FIGURE, FIGURE, FIGURE, null),
                        needs),
                refusal("a figure negative", () -> new RetirementFundHistory(FIGURE, FIGURE, FIGURE, FIGURE, Money.of("-1")),
                        negatives));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    void construct_whenOneFieldIsBad_isRefusedNamingIt(String badField, ThrowingCallable construct, String message) {
        // When / Then
        assertThatThrownBy(construct).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }
}
