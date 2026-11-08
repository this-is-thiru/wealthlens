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

/** What {@link EmployerShareAllotment} refuses, one bad field at a time; each message is the record's own (W-240a). */
class EmployerShareAllotmentTest {

    private static final LocalDate ALLOTTED = LocalDate.of(2026, 6, 10);
    private static final LocalDate EXERCISED = LocalDate.of(2026, 6, 1);
    private static final Money PRICE = Money.of("10");
    private static final FairMarketValueBasis TRADED =
            new FairMarketValueBasis.ListedTraded(Money.of("100"), Money.of("102"));

    @Test
    void construct_whenAllottedAfterTheExercise_isAccepted() {
        // When
        EmployerShareAllotment allotment = new EmployerShareAllotment(0, ALLOTTED, EXERCISED, 100, PRICE, TRADED);

        // Then
        assertThat(allotment.units()).isEqualTo(100);
    }

    static Stream<Arguments> refusals() {
        String needs = "A share allotment needs its dates, price and fair-market-value basis";
        String negatives = "A share allotment's position, units and price may not be negative";
        return Stream.of(
                refusal("allottedOn null", () -> new EmployerShareAllotment(0, null, EXERCISED, 100, PRICE, TRADED),
                        needs),
                refusal("exercisedOn null", () -> new EmployerShareAllotment(0, ALLOTTED, null, 100, PRICE, TRADED),
                        needs),
                refusal("pricePaidPerUnit null", () -> new EmployerShareAllotment(0, ALLOTTED, EXERCISED, 100, null,
                        TRADED), needs),
                refusal("basis null", () -> new EmployerShareAllotment(0, ALLOTTED, EXERCISED, 100, PRICE, null), needs),
                refusal("employment negative", () -> new EmployerShareAllotment(-1, ALLOTTED, EXERCISED, 100, PRICE,
                        TRADED), negatives),
                refusal("units negative", () -> new EmployerShareAllotment(0, ALLOTTED, EXERCISED, -1, PRICE, TRADED),
                        negatives),
                refusal("price negative", () -> new EmployerShareAllotment(0, ALLOTTED, EXERCISED, 100,
                        Money.of("-1"), TRADED), negatives),
                refusal("untraded price dated on the exercise day", () -> new EmployerShareAllotment(0, ALLOTTED,
                        EXERCISED, 100, PRICE, new FairMarketValueBasis.ListedNotTraded(Money.of("100"), EXERCISED)),
                        "An untraded share's closing price is from a day before the exercise date"),
                refusal("banker's date after the exercise", () -> new EmployerShareAllotment(0, ALLOTTED, EXERCISED,
                        100, PRICE, new FairMarketValueBasis.MerchantBankerValue(Money.of("100"), EXERCISED.plusDays(1))),
                        "A merchant banker's specified date is on or before the exercise date"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    void construct_whenOneFieldIsBad_isRefusedNamingIt(String badField, ThrowingCallable construct, String message) {
        // When / Then
        assertThatThrownBy(construct).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }
}
