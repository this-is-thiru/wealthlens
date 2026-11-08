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

/** What a claim of medical treatment abroad accepts and refuses (D-112). */
class MedicalTreatmentAbroadTest {

    @Test
    void construct_whenOneAttendant_isAccepted() {
        // When
        MedicalTreatmentAbroad claim = new MedicalTreatmentAbroad(
                0, Money.of("600000"), Money.of("150000"), Money.of("500000"), 1);

        // Then
        assertThat(claim.attendants()).isEqualTo(1);
    }

    @Test
    void construct_whenTwoAttendants_isRefusedQuotingTheAct() {
        // When / Then
        assertThatThrownBy(() -> new MedicalTreatmentAbroad(
                        0, Money.of("600000"), Money.of("150000"), Money.of("500000"), 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("one attendant who accompanies the patient");
    }

    @Test
    void construct_whenAnAmountIsNegative_isRefused() {
        // When / Then
        assertThatThrownBy(() -> new MedicalTreatmentAbroad(
                        0, Money.of("-1"), Money.ZERO, Money.ZERO, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void construct_whenAnAmountIsMissing_isRefused() {
        // When / Then
        assertThatThrownBy(() -> new MedicalTreatmentAbroad(0, Money.ZERO, null, Money.ZERO, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Each constructor argument made bad on its own (W-240b). */
    static Stream<Arguments> refusals() {
        Money cost = Money.of("500000");
        Money negative = Money.of("-1");
        String needs = "Medical treatment abroad needs the treatment-and-stay cost, the travel cost and the amount";
        String negatives = "Medical treatment abroad amounts may not be negative";
        return Stream.of(
                refusal("treatmentAndStay null", () -> new MedicalTreatmentAbroad(0, null, cost, cost, 0), needs),
                refusal("permittedByReserveBank null", () -> new MedicalTreatmentAbroad(0, cost, cost, null, 0), needs),
                refusal("employment negative", () -> new MedicalTreatmentAbroad(-1, cost, cost, cost, 0),
                        "An employment position may not be negative, but was -1"),
                refusal("treatmentAndStay negative", () -> new MedicalTreatmentAbroad(0, negative, cost, cost, 0),
                        negatives),
                refusal("travel negative", () -> new MedicalTreatmentAbroad(0, cost, negative, cost, 0), negatives),
                refusal("permittedByReserveBank negative", () -> new MedicalTreatmentAbroad(0, cost, cost, negative, 0),
                        negatives),
                refusal("attendants negative", () -> new MedicalTreatmentAbroad(0, cost, cost, cost, -1),
                        "The exclusion covers \"one attendant who accompanies the patient\""));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    void construct_whenOneFieldIsBad_isRefusedNamingIt(String badField, ThrowingCallable construct, String message) {
        // When / Then
        assertThatThrownBy(construct).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }
}
