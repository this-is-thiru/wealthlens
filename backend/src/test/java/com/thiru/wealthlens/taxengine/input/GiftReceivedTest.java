package com.thiru.wealthlens.taxengine.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** A gift received (S2 s.92(2)(m); 1961 s.56(2)(x); D-201). Figures are fixtures. */
class GiftReceivedTest {

    @Test
    void construct_whenAPropertyIsBoughtBelowItsValue_carriesEachFact() {
        // When
        GiftReceived flat = new GiftReceived(GiftKind.IMMOVABLE_PROPERTY, Money.of("5000000"), Money.of("5600000"),
                Optional.empty());

        // Then
        assertThat(flat.consideration()).isEqualTo(Money.of("5000000"));
        assertThat(flat.value()).isEqualTo(Money.of("5600000"));
        assertThat(flat.excludedAs()).isEmpty();
    }

    @Test
    void construct_whenAFigureIsNegativeOrMissing_isRefused() {
        assertThatThrownBy(() -> new GiftReceived(GiftKind.OTHER_PROPERTY, Money.ZERO, Money.of("-1"),
                        Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GiftReceived(GiftKind.OTHER_PROPERTY, null, Money.ZERO, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GiftReceived(null, Money.ZERO, Money.ZERO, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GiftReceived(GiftKind.SUM_OF_MONEY, Money.ZERO, Money.ZERO, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
