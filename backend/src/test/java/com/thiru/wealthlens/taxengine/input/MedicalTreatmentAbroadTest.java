package com.thiru.wealthlens.taxengine.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import org.junit.jupiter.api.Test;

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
}
