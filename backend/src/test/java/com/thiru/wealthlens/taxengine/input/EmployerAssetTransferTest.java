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

/** What {@link EmployerAssetTransfer} refuses, one bad field at a time; each message is the record's own (W-240a). */
class EmployerAssetTransferTest {

    private static final TransferredAssetKind CAR = TransferredAssetKind.MOTOR_CAR;
    private static final Money COST = Money.of("800000");
    private static final Money PRICE = Money.of("300000");
    private static final Money NEGATIVE = Money.of("-1");

    @Test
    void construct_whenACarIsSoldAfterTwoYears_isAccepted() {
        // When
        EmployerAssetTransfer transfer = new EmployerAssetTransfer(0, CAR, COST, 2, PRICE);

        // Then
        assertThat(transfer.completedYears()).isEqualTo(2);
    }

    static Stream<Arguments> refusals() {
        String needs = "A transferred asset needs its kind, cost and price";
        String negatives = "A transferred asset's position, years and amounts may not be negative";
        return Stream.of(
                refusal("kind null", () -> new EmployerAssetTransfer(0, null, COST, 2, PRICE), needs),
                refusal("cost null", () -> new EmployerAssetTransfer(0, CAR, null, 2, PRICE), needs),
                refusal("pricePaid null", () -> new EmployerAssetTransfer(0, CAR, COST, 2, null), needs),
                refusal("employment negative", () -> new EmployerAssetTransfer(-1, CAR, COST, 2, PRICE), negatives),
                refusal("completedYears negative", () -> new EmployerAssetTransfer(0, CAR, COST, -1, PRICE), negatives),
                refusal("cost negative", () -> new EmployerAssetTransfer(0, CAR, NEGATIVE, 2, PRICE), negatives),
                refusal("pricePaid negative", () -> new EmployerAssetTransfer(0, CAR, COST, 2, NEGATIVE), negatives));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    void construct_whenOneFieldIsBad_isRefusedNamingIt(String badField, ThrowingCallable construct, String message) {
        // When / Then
        assertThatThrownBy(construct).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }
}
