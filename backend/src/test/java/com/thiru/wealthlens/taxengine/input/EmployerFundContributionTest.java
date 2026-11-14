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

/** What {@link EmployerFundContribution} refuses, one bad field at a time; each message is the record's own (W-240a). */
class EmployerFundContributionTest {

    private static final EmployerFund FUND = EmployerFund.RECOGNISED_PROVIDENT_FUND;
    private static final Money AMOUNT = Money.of("100000");

    @Test
    void construct_whenAProvidentFundContribution_isAccepted() {
        // When
        EmployerFundContribution contribution = new EmployerFundContribution(0, FUND, AMOUNT);

        // Then
        assertThat(contribution.amount()).isEqualTo(AMOUNT);
    }

    static Stream<Arguments> refusals() {
        String needs = "An employer contribution needs its fund and amount";
        String negatives = "An employer contribution's position and amount may not be negative";
        return Stream.of(
                refusal("fund null", () -> new EmployerFundContribution(0, null, AMOUNT), needs),
                refusal("amount null", () -> new EmployerFundContribution(0, FUND, null), needs),
                refusal("employment negative", () -> new EmployerFundContribution(-1, FUND, AMOUNT), negatives),
                refusal("amount negative", () -> new EmployerFundContribution(0, FUND, Money.of("-1")), negatives));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    void construct_whenOneFieldIsBad_isRefusedNamingIt(String badField, ThrowingCallable construct, String message) {
        // When / Then
        assertThatThrownBy(construct).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }
}
