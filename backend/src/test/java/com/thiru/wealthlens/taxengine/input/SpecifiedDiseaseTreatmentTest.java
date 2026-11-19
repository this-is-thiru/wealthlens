package com.thiru.wealthlens.taxengine.input;

import static com.thiru.wealthlens.taxengine.input.InputRefusals.refusal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Optional;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** What {@link SpecifiedDiseaseTreatment} refuses, one bad field at a time; each message is the record's own (W-240b). */
class SpecifiedDiseaseTreatmentTest {

    private static final Money PAID = Money.of("40000");

    @Test
    void construct_whenEveryFieldIsValid_isAccepted() {
        // When
        SpecifiedDiseaseTreatment valid = new SpecifiedDiseaseTreatment(true, Optional.empty(), PAID, Money.ZERO);

        // Then
        assertThat(valid.paid()).isEqualTo(PAID);
    }

    static Stream<Arguments> refusals() {
        String needs = "Specified-disease treatment needs whom, what was paid and reimbursed";
        String valid = "Specified-disease treatment's amounts and position must be valid";
        return Stream.of(
                refusal("familyMember null", () -> new SpecifiedDiseaseTreatment(true, null, PAID, Money.ZERO),
                        needs),
                refusal("paid null", () -> new SpecifiedDiseaseTreatment(true, Optional.empty(), null, Money.ZERO),
                        needs),
                refusal("reimbursed null", () -> new SpecifiedDiseaseTreatment(true, Optional.empty(), PAID, null),
                        needs),
                refusal("for nobody", () -> new SpecifiedDiseaseTreatment(false, Optional.empty(), PAID, Money.ZERO),
                        "for the assessee or one family member"),
                refusal("paid negative", () -> new SpecifiedDiseaseTreatment(true, Optional.empty(), Money.of("-1"), Money.ZERO),
                        valid),
                refusal("reimbursed negative", () -> new SpecifiedDiseaseTreatment(true, Optional.empty(), PAID, Money.of("-1")),
                        valid),
                refusal("family position negative", () -> new SpecifiedDiseaseTreatment(false, Optional.of(-1), PAID, Money.ZERO),
                        valid));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    void construct_whenOneFieldIsBad_isRefusedNamingIt(String badField, ThrowingCallable construct, String message) {
        // When / Then
        assertThatThrownBy(construct).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }
}
