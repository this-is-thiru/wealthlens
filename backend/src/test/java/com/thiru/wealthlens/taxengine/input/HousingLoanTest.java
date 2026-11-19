package com.thiru.wealthlens.taxengine.input;

import static com.thiru.wealthlens.taxengine.input.InputRefusals.refusal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** What {@link HousingLoan} refuses, one bad field at a time; each message is the record's own (W-240b). */
class HousingLoanTest {

    private static final LocalDate BORROWED = LocalDate.of(2024, 4, 1);
    private static final Money INTEREST = Money.of("200000");

    @Test
    void construct_whenEveryFieldIsValid_isAccepted() {
        // When
        HousingLoan valid = new HousingLoan(LoanUse.ACQUISITION_OR_CONSTRUCTION, BORROWED, INTEREST, Money.ZERO, Money.ZERO, true, false);

        // Then
        assertThat(valid.interestPayable()).isEqualTo(INTEREST);
    }

    static Stream<Arguments> refusals() {
        String needs = "A housing loan needs its use and the date the capital was borrowed";
        String figures = "A housing loan needs every figure, none negative";
        return Stream.of(
                refusal("use null", () -> new HousingLoan(null, BORROWED, INTEREST, Money.ZERO, Money.ZERO, true, false),
                        needs),
                refusal("borrowedOn null", () -> new HousingLoan(LoanUse.ACQUISITION_OR_CONSTRUCTION, null, INTEREST, Money.ZERO, Money.ZERO, true, false),
                        needs),
                refusal("a figure null", () -> new HousingLoan(LoanUse.ACQUISITION_OR_CONSTRUCTION, BORROWED, INTEREST, null, Money.ZERO, true, false),
                        figures),
                refusal("a figure negative", () -> new HousingLoan(LoanUse.ACQUISITION_OR_CONSTRUCTION, BORROWED, INTEREST, Money.of("-1"), Money.ZERO, true, false),
                        figures),
                refusal("deducted under chapter eight above the interest", () -> new HousingLoan(LoanUse.ACQUISITION_OR_CONSTRUCTION, BORROWED, INTEREST, Money.ZERO, Money.of("200001"), true, false),
                        "is more than the"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    void construct_whenOneFieldIsBad_isRefusedNamingIt(String badField, ThrowingCallable construct, String message) {
        // When / Then
        assertThatThrownBy(construct).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }
}
