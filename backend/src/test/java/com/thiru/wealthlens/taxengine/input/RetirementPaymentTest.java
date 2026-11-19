package com.thiru.wealthlens.taxengine.input;

import static com.thiru.wealthlens.taxengine.input.InputRefusals.refusal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;
import java.util.Optional;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** A retirement or termination payment, as entered (S2 s.19(1) Table; 1961 s.10(10)–(10C); D-103). Fixtures. */
class RetirementPaymentTest {

    private static final ComponentCode GRATUITY = new ComponentCode("GRATUITY");
    private static final ComponentCode COMMUTED_PENSION = new ComponentCode("COMMUTED_PENSION");
    private static final ComponentCode VRS_COMPENSATION = new ComponentCode("VRS_COMPENSATION");

    @Test
    void of_whenAnEntireAmountEntry_carriesNoOtherFact() {
        // When
        RetirementPayment payment = RetirementPayment.of(0, GRATUITY, RetirementEntry.GOVERNMENT_GRATUITY);

        // Then
        assertThat(payment.entry()).isEqualTo(RetirementEntry.GOVERNMENT_GRATUITY);
        assertThat(payment.commutedValueOfWholePension()).isEmpty();
        assertThat(payment.arrearsReliefAllowed()).isFalse();
    }

    @Test
    void otherEmployerCommutation_carriesTheCommutedValue() {
        // When — Sl. 8: the limit is a fraction of the whole pension's commuted value
        RetirementPayment payment =
                RetirementPayment.otherEmployerCommutation(0, COMMUTED_PENSION, Money.of("900000"));

        // Then
        assertThat(payment.commutedValueOfWholePension()).contains(Money.of("900000"));
    }

    @Test
    void voluntaryRetirement_carriesWhetherArrearsReliefWasAllowed() {
        // When — s.19(2)(e)(iii); 1961 s.10(10C) third proviso
        RetirementPayment payment = RetirementPayment.voluntaryRetirement(0, VRS_COMPENSATION, true);

        // Then
        assertThat(payment.arrearsReliefAllowed()).isTrue();
    }

    @Test
    void construct_whenTheCommutedValueIsNotPositive_isRefused() {
        assertThatThrownBy(() -> RetirementPayment.otherEmployerCommutation(0, COMMUTED_PENSION, Money.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void otherGratuity_carriesTheSeparation() {
        // When — Sl. 6 is measured on the service and the ten months before the event (D-106)
        Separation separation = new Separation(LocalDate.of(2000, 4, 1), LocalDate.of(2025, 6, 30),
                SeparationTest.tenMonthsBefore(LocalDate.of(2025, 6, 30), "100000"));
        RetirementPayment payment = RetirementPayment.otherGratuity(0, GRATUITY, separation);

        // Then
        assertThat(payment.separation()).contains(separation);
        assertThat(payment.entry()).isEqualTo(RetirementEntry.OTHER_GRATUITY);
    }

    @Test
    void statutoryGratuity_carriesTheEmployersFigure() {
        // When — Sl. 5: the amount calculated under the gratuity statute (D-109)
        StatutoryEntitlement entitlement = new StatutoryEntitlement(LocalDate.of(2025, 12, 15), Money.of("1153846.15"));
        RetirementPayment payment = RetirementPayment.statutory(0, GRATUITY, RetirementEntry.STATUTORY_GRATUITY,
                entitlement);

        // Then
        assertThat(payment.statutory()).contains(entitlement);
    }


    /** Each constructor argument made bad on its own (W-240b). */
    static Stream<Arguments> refusals() {
        RetirementEntry entry = RetirementEntry.GOVERNMENT_GRATUITY;
        String needs = "A retirement payment needs its component and entry";
        return Stream.of(
                refusal("component null", () -> new RetirementPayment(0, null, entry, Optional.empty(), false,
                        Optional.empty(), Optional.empty(), Optional.empty()), needs),
                refusal("entry null", () -> new RetirementPayment(0, GRATUITY, null, Optional.empty(), false,
                        Optional.empty(), Optional.empty(), Optional.empty()), needs),
                refusal("commutedValueOfWholePension null", () -> new RetirementPayment(0, GRATUITY, entry, null, false,
                        Optional.empty(), Optional.empty(), Optional.empty()), needs),
                refusal("separation null", () -> new RetirementPayment(0, GRATUITY, entry, Optional.empty(), false, null,
                        Optional.empty(), Optional.empty()), needs),
                refusal("leave null", () -> new RetirementPayment(0, GRATUITY, entry, Optional.empty(), false,
                        Optional.empty(), null, Optional.empty()), needs),
                refusal("statutory null", () -> new RetirementPayment(0, GRATUITY, entry, Optional.empty(), false,
                        Optional.empty(), Optional.empty(), null), needs),
                refusal("employment negative", () -> new RetirementPayment(-1, GRATUITY, entry, Optional.empty(), false,
                        Optional.empty(), Optional.empty(), Optional.empty()),
                        "GRATUITY: the employment that paid it is an index from 0, but was -1"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    void construct_whenOneFieldIsBad_isRefusedNamingIt(String badField, ThrowingCallable construct, String message) {
        // When / Then
        assertThatThrownBy(construct).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }
}
