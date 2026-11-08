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

/** What {@link EmployerGift} refuses, one bad field at a time; each message is the record's own (W-240a). */
class EmployerGiftTest {

    @Test
    void construct_whenAGiftHasAValue_isAccepted() {
        // When
        EmployerGift gift = new EmployerGift(0, Money.of("5000"));

        // Then
        assertThat(gift.amount()).isEqualTo(Money.of("5000"));
    }

    static Stream<Arguments> refusals() {
        String value = "A gift needs a value of nil or more";
        return Stream.of(
                refusal("amount null", () -> new EmployerGift(0, null), value),
                refusal("amount negative", () -> new EmployerGift(0, Money.of("-1")), value),
                refusal("employment negative", () -> new EmployerGift(-1, Money.of("5000")),
                        "An employment position may not be negative, but was -1"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    void construct_whenOneFieldIsBad_isRefusedNamingIt(String badField, ThrowingCallable construct, String message) {
        // When / Then
        assertThatThrownBy(construct).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }
}
