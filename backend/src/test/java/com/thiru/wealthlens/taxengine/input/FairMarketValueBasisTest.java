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

/** What {@link FairMarketValueBasis} refuses, one bad field at a time; each message is the record's own (W-240b). */
class FairMarketValueBasisTest {

    private static final Money PRICE = Money.of("100");
    private static final LocalDate DAY = LocalDate.of(2026, 5, 29);

    @Test
    void construct_whenEveryFieldIsValid_isAccepted() {
        // When
        FairMarketValueBasis.ListedTraded valid = new FairMarketValueBasis.ListedTraded(PRICE, PRICE);

        // Then
        assertThat(valid.closingPrice()).isEqualTo(PRICE);
    }

    static Stream<Arguments> refusals() {
        String traded = "A listed share needs its opening and closing prices, nil or more";
        String untraded = "An untraded listed share needs a closing price and its date";
        String banker = "A merchant banker's value needs an amount and its date";
        return Stream.of(
                refusal("traded opening null", () -> new FairMarketValueBasis.ListedTraded(null, PRICE),
                        traded),
                refusal("traded closing null", () -> new FairMarketValueBasis.ListedTraded(PRICE, null),
                        traded),
                refusal("traded opening negative", () -> new FairMarketValueBasis.ListedTraded(Money.of("-1"), PRICE),
                        traded),
                refusal("traded closing negative", () -> new FairMarketValueBasis.ListedTraded(PRICE, Money.of("-1")),
                        traded),
                refusal("untraded price null", () -> new FairMarketValueBasis.ListedNotTraded(null, DAY),
                        untraded),
                refusal("untraded date null", () -> new FairMarketValueBasis.ListedNotTraded(PRICE, null),
                        untraded),
                refusal("untraded price negative", () -> new FairMarketValueBasis.ListedNotTraded(Money.of("-1"), DAY),
                        untraded),
                refusal("banker value null", () -> new FairMarketValueBasis.MerchantBankerValue(null, DAY),
                        banker),
                refusal("banker date null", () -> new FairMarketValueBasis.MerchantBankerValue(PRICE, null),
                        banker),
                refusal("banker value negative", () -> new FairMarketValueBasis.MerchantBankerValue(Money.of("-1"), DAY),
                        banker));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    void construct_whenOneFieldIsBad_isRefusedNamingIt(String badField, ThrowingCallable construct, String message) {
        // When / Then
        assertThatThrownBy(construct).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }
}
