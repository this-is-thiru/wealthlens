package com.thiru.wealthlens.taxengine.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import org.junit.jupiter.api.Test;

/** A person's own provident fund account for the year (S4 r.277; Rules 1962 r.9D; D-198). Figures are fixtures. */
class ProvidentFundAccountTest {

    @Test
    void construct_whenEveryFigureIsGiven_carriesEachFact() {
        // When
        ProvidentFundAccount account = new ProvidentFundAccount(Money.of("600000"), true, Money.of("12000"));

        // Then
        assertThat(account.contributed()).isEqualTo(Money.of("600000"));
        assertThat(account.employerContributes()).isTrue();
        assertThat(account.taxableAccountInterest()).isEqualTo(Money.of("12000"));
    }

    @Test
    void construct_whenAFigureIsNegative_isRefused() {
        assertThatThrownBy(() -> new ProvidentFundAccount(Money.of("-1"), true, Money.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("neither negative");
        assertThatThrownBy(() -> new ProvidentFundAccount(Money.ZERO, false, Money.of("-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("neither negative");
    }

    @Test
    void construct_whenAFigureIsMissing_isRefused() {
        assertThatThrownBy(() -> new ProvidentFundAccount(null, true, Money.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProvidentFundAccount(Money.ZERO, true, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
