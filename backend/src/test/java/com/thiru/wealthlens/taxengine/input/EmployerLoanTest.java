package com.thiru.wealthlens.taxengine.input;

import static com.thiru.wealthlens.taxengine.input.InputRefusals.refusal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** What {@link EmployerLoan} refuses, one bad field at a time; each message is the record's own (W-240a). */
class EmployerLoanTest {

    private static final Money LENT = Money.of("500000");
    private static final Rate RATE = Rate.percent("9");
    private static final List<Money> BALANCES = List.of(Money.of("400000"));
    private static final Money NEGATIVE = Money.of("-1");

    @Test
    void construct_whenEveryFieldIsGiven_isAccepted() {
        // When
        EmployerLoan loan = new EmployerLoan(0, LENT, RATE, BALANCES, Money.ZERO, false, Optional.empty());

        // Then
        assertThat(loan.monthEndBalances()).containsExactly(Money.of("400000"));
    }

    static Stream<Arguments> refusals() {
        String needs = "A loan needs the amount lent, the Bank's rate, the balances and the interest paid";
        String amounts = "Loan amounts may not be negative or missing";
        String reimbursement = "A reimbursement needs an amount of nil or more and a month";
        return Stream.of(
                refusal("amountLent null", () -> new EmployerLoan(0, null, RATE, BALANCES, Money.ZERO, false,
                        Optional.empty()), needs),
                refusal("stateBankRate null", () -> new EmployerLoan(0, LENT, null, BALANCES, Money.ZERO, false,
                        Optional.empty()), needs),
                refusal("monthEndBalances null", () -> new EmployerLoan(0, LENT, RATE, null, Money.ZERO, false,
                        Optional.empty()), needs),
                refusal("interestPaid null", () -> new EmployerLoan(0, LENT, RATE, BALANCES, null, false,
                        Optional.empty()), needs),
                refusal("insuranceReimbursement null", () -> new EmployerLoan(0, LENT, RATE, BALANCES, Money.ZERO,
                        false, null), needs),
                refusal("employment negative", () -> new EmployerLoan(-1, LENT, RATE, BALANCES, Money.ZERO, false,
                        Optional.empty()), "An employment position may not be negative, but was -1"),
                refusal("amountLent negative", () -> new EmployerLoan(0, NEGATIVE, RATE, BALANCES, Money.ZERO, false,
                        Optional.empty()), amounts),
                refusal("interestPaid negative", () -> new EmployerLoan(0, LENT, RATE, BALANCES, NEGATIVE, false,
                        Optional.empty()), amounts),
                refusal("a balance missing", () -> new EmployerLoan(0, LENT, RATE, Arrays.asList((Money) null),
                        Money.ZERO, false, Optional.empty()), amounts),
                refusal("a balance negative", () -> new EmployerLoan(0, LENT, RATE, List.of(NEGATIVE), Money.ZERO,
                        false, Optional.empty()), amounts),
                refusal("stateBankRate negative", () -> new EmployerLoan(0, LENT, Rate.percent("-1"), BALANCES,
                        Money.ZERO, false, Optional.empty()), "The Bank's rate may not be negative"),
                refusal("reimbursement amount null", () -> new EmployerLoan.InsuranceReimbursement(null, 1),
                        reimbursement),
                refusal("reimbursement amount negative", () -> new EmployerLoan.InsuranceReimbursement(NEGATIVE, 1),
                        reimbursement),
                refusal("reimbursement month negative", () -> new EmployerLoan.InsuranceReimbursement(Money.ZERO, -1),
                        reimbursement));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    void construct_whenOneFieldIsBad_isRefusedNamingIt(String badField, ThrowingCallable construct, String message) {
        // When / Then
        assertThatThrownBy(construct).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }
}
