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

/** What {@link EmployerHouseholdBenefit} refuses, one bad field at a time; each message is the record's own (W-240a). */
class EmployerHouseholdBenefitTest {

    private static final HouseholdBenefitKind SERVICES = HouseholdBenefitKind.SERVICES;
    private static final BenefitArrangement PROVIDED = BenefitArrangement.EMPLOYER_PROVIDED;
    private static final Money AMOUNT = Money.of("50000");
    private static final Money NEGATIVE = Money.of("-1");

    @Test
    void construct_whenTheEmployerProvidesAServant_isAccepted() {
        // When
        EmployerHouseholdBenefit benefit = new EmployerHouseholdBenefit(0, SERVICES, PROVIDED, AMOUNT, Money.ZERO, false);

        // Then
        assertThat(benefit.amount()).isEqualTo(AMOUNT);
    }

    static Stream<Arguments> refusals() {
        String needs = "A household benefit needs its kind, arrangement, amount and recovery";
        String negatives = "An employment position and benefit amounts may not be negative";
        return Stream.of(
                refusal("kind null", () -> new EmployerHouseholdBenefit(0, null, PROVIDED, AMOUNT, Money.ZERO, false),
                        needs),
                refusal("arrangement null", () -> new EmployerHouseholdBenefit(0, SERVICES, null, AMOUNT, Money.ZERO,
                        false), needs),
                refusal("amount null", () -> new EmployerHouseholdBenefit(0, SERVICES, PROVIDED, null, Money.ZERO,
                        false), needs),
                refusal("recovered null", () -> new EmployerHouseholdBenefit(0, SERVICES, PROVIDED, AMOUNT, null,
                        false), needs),
                refusal("employment negative", () -> new EmployerHouseholdBenefit(-1, SERVICES, PROVIDED, AMOUNT,
                        Money.ZERO, false), negatives),
                refusal("amount negative", () -> new EmployerHouseholdBenefit(0, SERVICES, PROVIDED, NEGATIVE,
                        Money.ZERO, false), negatives),
                refusal("recovered negative", () -> new EmployerHouseholdBenefit(0, SERVICES, PROVIDED, AMOUNT,
                        NEGATIVE, false), negatives));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    void construct_whenOneFieldIsBad_isRefusedNamingIt(String badField, ThrowingCallable construct, String message) {
        // When / Then
        assertThatThrownBy(construct).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }
}
