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

/** What {@link EmployerChildEducation} refuses, one bad field at a time; each message is the record's own (W-240a). */
class EmployerChildEducationTest {

    private static final Money COST = Money.of("2000");
    private static final Money NEGATIVE = Money.of("-1");

    @Test
    void construct_whenAYearAtTheEmployersSchool_isAccepted() {
        // When
        EmployerChildEducation education = new EmployerChildEducation(0, true, COST, 12, Money.ZERO);

        // Then
        assertThat(education.months()).isEqualTo(12);
    }

    static Stream<Arguments> refusals() {
        String needs = "A child's education needs the cost per month and the recovery";
        String ranges = "A child's education needs a position, 0 to 12 months, and amounts of nil or more";
        return Stream.of(
                refusal("cost null", () -> new EmployerChildEducation(0, true, null, 12, Money.ZERO), needs),
                refusal("recovered null", () -> new EmployerChildEducation(0, true, COST, 12, null), needs),
                refusal("employment negative", () -> new EmployerChildEducation(-1, true, COST, 12, Money.ZERO), ranges),
                refusal("months negative", () -> new EmployerChildEducation(0, true, COST, -1, Money.ZERO), ranges),
                refusal("thirteen months", () -> new EmployerChildEducation(0, true, COST, 13, Money.ZERO), ranges),
                refusal("cost negative", () -> new EmployerChildEducation(0, true, NEGATIVE, 12, Money.ZERO), ranges),
                refusal("recovered negative", () -> new EmployerChildEducation(0, true, COST, 12, NEGATIVE), ranges));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    void construct_whenOneFieldIsBad_isRefusedNamingIt(String badField, ThrowingCallable construct, String message) {
        // When / Then
        assertThatThrownBy(construct).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }
}
