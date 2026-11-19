package com.thiru.wealthlens.taxengine.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import org.junit.jupiter.api.Test;

/** A business trust's specified sum (S2 s.92(2)(k); 1961 s.56(2)(xii); D-203): A − B − C, never below nil. By hand. */
class BusinessTrustUnitsTest {

    @Test
    void specifiedSum_whenADistributedMoreThanIssueAndEarlierCharges_isTheRest() {
        // 1,50,000 − 1,00,000 − 20,000
        assertThat(new BusinessTrustUnits(Money.of("150000"), Money.of("100000"), Money.of("20000")).specifiedSum())
                .isEqualTo(Money.of("30000"));
    }

    @Test
    void specifiedSum_whenIssueAndEarlierChargesExceedTheDistributions_isNil() {
        // 80,000 + 30,000 = 1,10,000 > 1,00,000
        assertThat(new BusinessTrustUnits(Money.of("100000"), Money.of("80000"), Money.of("30000")).specifiedSum())
                .isEqualTo(Money.ZERO);
    }

    @Test
    void specifiedSum_whenTheyAreEqual_isNil() {
        assertThat(new BusinessTrustUnits(Money.of("110000"), Money.of("80000"), Money.of("30000")).specifiedSum())
                .isEqualTo(Money.ZERO);
    }

    @Test
    void construct_whenAFigureIsNegativeOrMissing_isRefused() {
        assertThatThrownBy(() -> new BusinessTrustUnits(Money.of("-1"), Money.ZERO, Money.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BusinessTrustUnits(Money.ZERO, null, Money.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
