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

/** What {@link HouseReinvestment} refuses, one bad field at a time; each message is the record's own (W-240b). */
class HouseReinvestmentTest {

    private static final LocalDate SOLD = LocalDate.of(2026, 5, 1);
    private static final LocalDate BOUGHT = LocalDate.of(2026, 9, 1);
    private static final Money COST = Money.of("9000000");

    @Test
    void construct_whenEveryFieldIsValid_isAccepted() {
        // When
        HouseReinvestment valid = new HouseReinvestment(SOLD, BOUGHT, false, COST, 0, 0);

        // Then
        assertThat(valid.newAssetCost()).isEqualTo(COST);
    }

    static Stream<Arguments> refusals() {
        String needs = "A reinvestment needs the original transfer's date, the new house's date and its cost";
        String counts = "Counts of other houses may not be negative";
        return Stream.of(
                refusal("transferDate null", () -> new HouseReinvestment(null, BOUGHT, false, COST, 0, 0),
                        needs),
                refusal("newAssetOn null", () -> new HouseReinvestment(SOLD, null, false, COST, 0, 0),
                        needs),
                refusal("newAssetCost null", () -> new HouseReinvestment(SOLD, BOUGHT, false, null, 0, 0),
                        needs),
                refusal("newAssetCost negative", () -> new HouseReinvestment(SOLD, BOUGHT, false, Money.of("-1"), 0, 0),
                        "The new house's cost may not be negative"),
                refusal("otherHousesOwned negative", () -> new HouseReinvestment(SOLD, BOUGHT, false, COST, -1, 0),
                        counts),
                refusal("otherHousesAcquired negative", () -> new HouseReinvestment(SOLD, BOUGHT, false, COST, 0, -1),
                        counts));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    void construct_whenOneFieldIsBad_isRefusedNamingIt(String badField, ThrowingCallable construct, String message) {
        // When / Then
        assertThatThrownBy(construct).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }
}
