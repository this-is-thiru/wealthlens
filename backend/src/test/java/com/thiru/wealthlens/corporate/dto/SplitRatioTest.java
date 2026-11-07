package com.thiru.wealthlens.corporate.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.testsupport.MoneyAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class SplitRatioTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "2", "2:", ":1", "a:1", "0:1", "2:0", "-2:1", "2:1:1", "1.5:1", " 2:1"})
    void parse_whenMalformed_refuses(String text) {
        // When / Then
        assertThatThrownBy(() -> SplitRatio.parse(text))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("A stock split's ratio is new shares to old shares");
    }

    @Test
    void parse_whenTwoToOne_doublesTheSharesAndHalvesThePrice() {
        // When
        SplitRatio ratio = SplitRatio.parse("2:1");

        // Then — 100 @ 1,000 becomes 200 @ 500
        assertThat(ratio.quantityAfter(100)).isEqualTo(200.0);
        MoneyAssert.assertMoney(500, ratio.priceAfter(1000));
    }

    @Test
    void parse_whenThreeToTwo_keepsTheCost() {
        // When
        SplitRatio ratio = SplitRatio.parse("3:2");

        // Then — 100 @ 1,000 becomes 150 at 666.67, still 1,00,000 together
        assertThat(ratio.quantityAfter(100)).isEqualTo(150.0);
        MoneyAssert.assertMoney(100000, ratio.quantityAfter(100) * ratio.priceAfter(1000));
    }

    @Test
    void parse_whenOneToTwo_consolidates() {
        // When
        SplitRatio ratio = SplitRatio.parse("1:2");

        // Then — 100 @ 1,000 becomes 50 @ 2,000
        assertThat(ratio.quantityAfter(100)).isEqualTo(50.0);
        MoneyAssert.assertMoney(2000, ratio.priceAfter(1000));
    }
}
