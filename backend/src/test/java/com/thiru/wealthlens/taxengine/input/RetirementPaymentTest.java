package com.thiru.wealthlens.taxengine.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** A retirement or termination payment, as entered (S2 s.19(1) Table; 1961 s.10(10)–(10C); D-103). Fixtures. */
class RetirementPaymentTest {

    private static final ComponentCode GRATUITY = new ComponentCode("GRATUITY");
    private static final ComponentCode COMMUTED_PENSION = new ComponentCode("COMMUTED_PENSION");
    private static final ComponentCode VRS_COMPENSATION = new ComponentCode("VRS_COMPENSATION");
    private static final ComponentCode LEAVE_ENCASHMENT = new ComponentCode("LEAVE_ENCASHMENT");

    @Test
    void of_whenAnEntireAmountEntry_carriesNoOtherFact() {
        // When
        RetirementPayment payment = RetirementPayment.of(GRATUITY, RetirementEntry.GOVERNMENT_GRATUITY);

        // Then
        assertThat(payment.entry()).isEqualTo(RetirementEntry.GOVERNMENT_GRATUITY);
        assertThat(payment.commutedValueOfWholePension()).isEmpty();
        assertThat(payment.arrearsReliefAllowed()).isFalse();
    }

    @Test
    void otherEmployerCommutation_carriesTheCommutedValue() {
        // When — Sl. 8: the limit is a fraction of the whole pension's commuted value
        RetirementPayment payment =
                RetirementPayment.otherEmployerCommutation(COMMUTED_PENSION, Money.of("900000"));

        // Then
        assertThat(payment.commutedValueOfWholePension()).contains(Money.of("900000"));
    }

    @Test
    void construct_whenAnOtherEmployerCommutationLacksTheCommutedValue_isRefused() {
        assertThatThrownBy(() -> new RetirementPayment(COMMUTED_PENSION, RetirementEntry.OTHER_EMPLOYER_COMMUTATION,
                        Optional.empty(), false, Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("commuted value");
    }

    @Test
    void construct_whenACommutedValueIsGivenForAnotherEntry_isRefusedRatherThanIgnored() {
        assertThatThrownBy(() -> new RetirementPayment(COMMUTED_PENSION, RetirementEntry.GOVERNMENT_COMMUTATION,
                        Optional.of(Money.of("900000")), false, Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GOVERNMENT_COMMUTATION");
    }

    @Test
    void construct_whenArrearsReliefIsGivenOutsideVoluntaryRetirement_isRefused() {
        assertThatThrownBy(() -> new RetirementPayment(GRATUITY, RetirementEntry.GOVERNMENT_GRATUITY,
                        Optional.empty(), true, Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("voluntary retirement");
    }

    @Test
    void voluntaryRetirement_carriesWhetherArrearsReliefWasAllowed() {
        // When — s.19(2)(e)(iii); 1961 s.10(10C) third proviso
        RetirementPayment payment = RetirementPayment.voluntaryRetirement(VRS_COMPENSATION, true);

        // Then
        assertThat(payment.arrearsReliefAllowed()).isTrue();
    }

    @Test
    void construct_whenTheCommutedValueIsNotPositive_isRefused() {
        assertThatThrownBy(() -> RetirementPayment.otherEmployerCommutation(COMMUTED_PENSION, Money.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void otherGratuity_carriesTheSeparation() {
        // When — Sl. 6 is measured on the service and the ten months before the event (D-106)
        Separation separation = new Separation(LocalDate.of(2000, 4, 1), LocalDate.of(2025, 6, 30),
                SeparationTest.tenMonthsBefore(LocalDate.of(2025, 6, 30), "100000"));
        RetirementPayment payment = RetirementPayment.otherGratuity(GRATUITY, separation);

        // Then
        assertThat(payment.separation()).contains(separation);
        assertThat(payment.entry()).isEqualTo(RetirementEntry.OTHER_GRATUITY);
    }

    @Test
    void construct_whenOtherGratuityLacksTheSeparation_isRefused() {
        assertThatThrownBy(() -> new RetirementPayment(GRATUITY, RetirementEntry.OTHER_GRATUITY, Optional.empty(),
                        false, Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("separation");
    }

    @Test
    void construct_whenOtherLeaveEncashmentLacksItsFacts_isRefused() {
        assertThatThrownBy(() -> new RetirementPayment(LEAVE_ENCASHMENT, RetirementEntry.OTHER_LEAVE_ENCASHMENT,
                        Optional.empty(), false, Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("leave");
    }

    @Test
    void construct_whenLeaveFactsAreGivenForAnotherEntry_isRefusedRatherThanIgnored() {
        LeaveAtRetirement leave = new LeaveAtRetirement(LocalDate.of(2025, 6, 30), Money.of("900000"),
                Money.of("100000"));

        assertThatThrownBy(() -> new RetirementPayment(LEAVE_ENCASHMENT,
                        RetirementEntry.GOVERNMENT_LEAVE_ENCASHMENT, Optional.empty(), false, Optional.empty(),
                        Optional.of(leave)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GOVERNMENT_LEAVE_ENCASHMENT");
    }
}
