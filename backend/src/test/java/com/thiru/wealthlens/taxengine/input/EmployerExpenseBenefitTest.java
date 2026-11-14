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

/** What {@link EmployerExpenseBenefit} refuses, one bad field at a time; each message is the record's own (W-240a). */
class EmployerExpenseBenefitTest {

    private static final ExpenseBenefitKind CLUB = ExpenseBenefitKind.CLUB;
    private static final Money AMOUNT = Money.of("20000");
    private static final OfficialUseRecord NONE = OfficialUseRecord.none();
    private static final Money NEGATIVE = Money.of("-1");

    @Test
    void construct_whenAClubBillIsPaid_isAccepted() {
        // When
        EmployerExpenseBenefit benefit = new EmployerExpenseBenefit(0, CLUB, AMOUNT, Money.ZERO, NONE, false, false);

        // Then
        assertThat(benefit.amount()).isEqualTo(AMOUNT);
    }

    static Stream<Arguments> refusals() {
        String needs = "An expense benefit needs its kind, amount, recovery and records";
        String negatives = "An employment position and expense amounts may not be negative";
        return Stream.of(
                refusal("kind null", () -> new EmployerExpenseBenefit(0, null, AMOUNT, Money.ZERO, NONE, false, false),
                        needs),
                refusal("amount null", () -> new EmployerExpenseBenefit(0, CLUB, null, Money.ZERO, NONE, false, false),
                        needs),
                refusal("recovered null", () -> new EmployerExpenseBenefit(0, CLUB, AMOUNT, null, NONE, false, false),
                        needs),
                refusal("officialUse null", () -> new EmployerExpenseBenefit(0, CLUB, AMOUNT, Money.ZERO, null, false,
                        false), needs),
                refusal("employment negative", () -> new EmployerExpenseBenefit(-1, CLUB, AMOUNT, Money.ZERO, NONE,
                        false, false), negatives),
                refusal("amount negative", () -> new EmployerExpenseBenefit(0, CLUB, NEGATIVE, Money.ZERO, NONE, false,
                        false), negatives),
                refusal("recovered negative", () -> new EmployerExpenseBenefit(0, CLUB, AMOUNT, NEGATIVE, NONE, false,
                        false), negatives));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    void construct_whenOneFieldIsBad_isRefusedNamingIt(String badField, ThrowingCallable construct, String message) {
        // When / Then
        assertThatThrownBy(construct).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }
}
