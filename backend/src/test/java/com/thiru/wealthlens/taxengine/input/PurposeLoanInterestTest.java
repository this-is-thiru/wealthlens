package com.thiru.wealthlens.taxengine.input;

import static com.thiru.wealthlens.taxengine.input.InputRefusals.refusal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;
import java.util.Optional;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** What {@link PurposeLoanInterest} refuses, one bad field at a time; each message is the record's own (W-240b). */
class PurposeLoanInterestTest {

    private static final LoanPurpose HOUSE = LoanPurpose.RESIDENTIAL_HOUSE;
    private static final Money INTEREST = Money.of("150000");
    private static final LocalDate SANCTIONED = LocalDate.of(2026, 5, 1);
    private static final Money SANCTION = Money.of("3000000");

    @Test
    void construct_whenEveryFieldIsValid_isAccepted() {
        // When
        PurposeLoanInterest valid = new PurposeLoanInterest(HOUSE, INTEREST, SANCTIONED, SANCTION, Optional.empty(), Optional.empty(), false);

        // Then
        assertThat(valid.interestPayable()).isEqualTo(INTEREST);
    }

    static Stream<Arguments> refusals() {
        String needs = "Loan interest needs its purpose, interest, sanction date and amount";
        String negatives = "Loan interest and the amount sanctioned may not be negative";
        return Stream.of(
                refusal("purpose null", () -> new PurposeLoanInterest(null, INTEREST, SANCTIONED, SANCTION, Optional.empty(), Optional.empty(), false),
                        needs),
                refusal("interestPayable null", () -> new PurposeLoanInterest(HOUSE, null, SANCTIONED, SANCTION, Optional.empty(), Optional.empty(), false),
                        needs),
                refusal("sanctionedOn null", () -> new PurposeLoanInterest(HOUSE, INTEREST, null, SANCTION, Optional.empty(), Optional.empty(), false),
                        needs),
                refusal("amountSanctioned null", () -> new PurposeLoanInterest(HOUSE, INTEREST, SANCTIONED, null, Optional.empty(), Optional.empty(), false),
                        needs),
                refusal("propertyValue null", () -> new PurposeLoanInterest(HOUSE, INTEREST, SANCTIONED, SANCTION, null, Optional.empty(), false),
                        needs),
                refusal("stampDutyValue null", () -> new PurposeLoanInterest(HOUSE, INTEREST, SANCTIONED, SANCTION, Optional.empty(), null, false),
                        needs),
                refusal("interestPayable negative", () -> new PurposeLoanInterest(HOUSE, Money.of("-1"), SANCTIONED, SANCTION, Optional.empty(), Optional.empty(), false),
                        negatives),
                refusal("amountSanctioned negative", () -> new PurposeLoanInterest(HOUSE, INTEREST, SANCTIONED, Money.of("-1"), Optional.empty(), Optional.empty(), false),
                        negatives));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    void construct_whenOneFieldIsBad_isRefusedNamingIt(String badField, ThrowingCallable construct, String message) {
        // When / Then
        assertThatThrownBy(construct).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }
}
