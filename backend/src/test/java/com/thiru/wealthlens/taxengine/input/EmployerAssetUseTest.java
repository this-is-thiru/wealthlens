package com.thiru.wealthlens.taxengine.input;

import static com.thiru.wealthlens.taxengine.input.InputRefusals.refusal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.definition.MovableAssetKind;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Optional;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** What {@link EmployerAssetUse} refuses, one bad field at a time; each message is the record's own (W-240a). */
class EmployerAssetUseTest {

    private static final MovableAssetKind LAPTOP = MovableAssetKind.LAPTOP_OR_COMPUTER;
    private static final Optional<Money> COST = Optional.of(Money.of("60000"));
    private static final Optional<Money> NEGATIVE = Optional.of(Money.of("-1"));

    @Test
    void construct_whenAnOwnedAssetIsLentForAYear_isAccepted() {
        // When
        EmployerAssetUse use = new EmployerAssetUse(0, LAPTOP, COST, Optional.empty(), 12, Money.ZERO);

        // Then
        assertThat(use.cost()).isEqualTo(COST);
    }

    static Stream<Arguments> refusals() {
        String needs = "An asset lent needs its kind and recovery";
        String counts = "An employment position may not be negative, and months lie in 0 to 12";
        String amounts = "Asset amounts may not be negative";
        return Stream.of(
                refusal("kind null", () -> new EmployerAssetUse(0, null, COST, Optional.empty(), 12, Money.ZERO), needs),
                refusal("cost null", () -> new EmployerAssetUse(0, LAPTOP, null, Optional.empty(), 12, Money.ZERO), needs),
                refusal("rentPaid null", () -> new EmployerAssetUse(0, LAPTOP, COST, null, 12, Money.ZERO), needs),
                refusal("recovered null", () -> new EmployerAssetUse(0, LAPTOP, COST, Optional.empty(), 12, null), needs),
                refusal("neither cost nor rent", () -> new EmployerAssetUse(0, LAPTOP, Optional.empty(), Optional.empty(),
                        12, Money.ZERO), "not neither"),
                refusal("employment negative", () -> new EmployerAssetUse(-1, LAPTOP, COST, Optional.empty(), 12,
                        Money.ZERO), counts),
                refusal("months negative", () -> new EmployerAssetUse(0, LAPTOP, COST, Optional.empty(), -1, Money.ZERO),
                        counts),
                refusal("thirteen months", () -> new EmployerAssetUse(0, LAPTOP, COST, Optional.empty(), 13, Money.ZERO),
                        counts),
                refusal("recovered negative", () -> new EmployerAssetUse(0, LAPTOP, COST, Optional.empty(), 12,
                        Money.of("-1")), amounts),
                refusal("cost negative", () -> new EmployerAssetUse(0, LAPTOP, NEGATIVE, Optional.empty(), 12,
                        Money.ZERO), amounts),
                refusal("rent negative", () -> new EmployerAssetUse(0, LAPTOP, Optional.empty(), NEGATIVE, 12,
                        Money.ZERO), amounts));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    void construct_whenOneFieldIsBad_isRefusedNamingIt(String badField, ThrowingCallable construct, String message) {
        // When / Then
        assertThatThrownBy(construct).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }
}
