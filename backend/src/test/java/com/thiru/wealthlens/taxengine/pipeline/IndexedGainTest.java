package com.thiru.wealthlens.taxengine.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * How a part's land gains are lowered when the part is (W-088g3 C3a, D-431): each in proportion, the indexed measure by
 * the same rupees and never below nil, and never more than the part between them. Figures by hand in
 * {@code plans/W-088g3.md}.
 */
class IndexedGainTest {

    private static IndexedGain gain(String gain, String indexed) {
        return new IndexedGain(Money.of(gain), Money.of(indexed));
    }

    @Test
    void lowered_whenThePartIsLowered_lowersEachGainInProportionAndItsMeasureByTheSameRupees() {
        // Given — the plot's 30,00,000 (indexed 7,17,948.72) in a part of 40,00,000 that the nil band lowers to 37,50,000
        List<IndexedGain> gains = List.of(gain("3000000", "717948.72"));

        // When
        List<IndexedGain> lowered = IndexedGain.lowered(gains, Money.of("4000000"), Money.of("3750000"));

        // Then — 30,00,000 × 37,50,000 ÷ 40,00,000 = 28,12,500; the measure loses the same 1,87,500
        assertThat(lowered).containsExactly(gain("2812500", "530448.72"));
    }

    @Test
    void lowered_whenRoundingWouldExceedThePart_takesThePaisaFromTheLast() {
        // Given — two gains of 0.50 making a part of 1.00, lowered to 0.03
        List<IndexedGain> gains = List.of(gain("0.50", "0.50"), gain("0.50", "0.50"));

        // When
        List<IndexedGain> lowered = IndexedGain.lowered(gains, Money.of("1.00"), Money.of("0.03"));

        // Then — each 0.015 rounds to 0.02, which would hold 0.04 of a 0.03 part, so the last gives up a paisa
        assertThat(lowered).containsExactly(gain("0.02", "0.02"), gain("0.01", "0.01"));
    }

    @Test
    void lowered_whenTheReductionExceedsTheIndexedMeasure_floorsItAtNil() {
        // Given — a gain of 10,00,000 whose indexed measure is 1,00,000, its part halved
        List<IndexedGain> gains = List.of(gain("1000000", "100000"));

        // When
        List<IndexedGain> lowered = IndexedGain.lowered(gains, Money.of("1000000"), Money.of("500000"));

        // Then — 5,00,000 left; the measure would lose 5,00,000 and stops at nil
        assertThat(lowered).containsExactly(gain("500000", "0"));
    }

    @Test
    void lowered_whenThePartIsLoweredToNil_keepsNone() {
        // Given / When
        List<IndexedGain> lowered = IndexedGain.lowered(List.of(gain("1000000", "100000")), Money.of("1000000"),
                Money.ZERO);

        // Then
        assertThat(lowered).isEmpty();
    }
}
