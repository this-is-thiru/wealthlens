package com.thiru.wealthlens.taxengine.input;

import static com.thiru.wealthlens.taxengine.input.InputRefusals.refusal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** What {@link HouseProperty} refuses, one bad field at a time; each message is the record's own (W-240b). */
class HousePropertyTest {

    private static final Money RENT = Money.of("240000");
    private static final Rate WHOLE = Rate.percent("100");

    @Test
    void construct_whenEveryFieldIsValid_isAccepted() {
        // When
        HouseProperty valid = new HouseProperty(HouseOccupancy.LET, RENT, RENT, Money.ZERO, false, Money.ZERO, WHOLE, Money.ZERO, Optional.empty(), List.of());

        // Then
        assertThat(valid.rentReceivable()).isEqualTo(RENT);
    }

    static Stream<Arguments> refusals() {
        String needs = "A house property needs its occupancy and the assessee's share";
        String figures = "A house property needs every figure, none negative";
        String share = "A share in a house property is more than nil and at most the whole";
        String dated = "A house property needs Optional.empty() where the acquisition date is not needed";
        return Stream.of(
                refusal("occupancy null", () -> new HouseProperty(null, RENT, RENT, Money.ZERO, false, Money.ZERO, WHOLE, Money.ZERO, Optional.empty(), List.of()),
                        needs),
                refusal("ownershipShare null", () -> new HouseProperty(HouseOccupancy.LET, RENT, RENT, Money.ZERO, false, Money.ZERO, null, Money.ZERO, Optional.empty(), List.of()),
                        needs),
                refusal("a figure null", () -> new HouseProperty(HouseOccupancy.LET, RENT, RENT, null, false, Money.ZERO, WHOLE, Money.ZERO, Optional.empty(), List.of()),
                        figures),
                refusal("a figure negative", () -> new HouseProperty(HouseOccupancy.LET, RENT, RENT, Money.ZERO, false, Money.ZERO, WHOLE, Money.of("-1"), Optional.empty(), List.of()),
                        figures),
                refusal("share nil", () -> new HouseProperty(HouseOccupancy.LET, RENT, RENT, Money.ZERO, false, Money.ZERO, Rate.ZERO, Money.ZERO, Optional.empty(), List.of()),
                        share),
                refusal("share over the whole", () -> new HouseProperty(HouseOccupancy.LET, RENT, RENT, Money.ZERO, false, Money.ZERO, Rate.percent("101"), Money.ZERO, Optional.empty(), List.of()),
                        share),
                refusal("vacant but not let", () -> new HouseProperty(HouseOccupancy.NOT_LET, Money.ZERO, Money.ZERO, Money.ZERO, true, Money.ZERO, WHOLE, Money.ZERO, Optional.empty(), List.of()),
                        "Rent or a vacancy means the house was let"),
                refusal("acquisition date null", () -> new HouseProperty(HouseOccupancy.LET, RENT, RENT, Money.ZERO, false, Money.ZERO, WHOLE, Money.ZERO, null, List.of()),
                        dated),
                refusal("loans null", () -> new HouseProperty(HouseOccupancy.LET, RENT, RENT, Money.ZERO, false, Money.ZERO, WHOLE, Money.ZERO, Optional.empty(), null),
                        dated));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    void construct_whenOneFieldIsBad_isRefusedNamingIt(String badField, ThrowingCallable construct, String message) {
        // When / Then
        assertThatThrownBy(construct).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }
}
