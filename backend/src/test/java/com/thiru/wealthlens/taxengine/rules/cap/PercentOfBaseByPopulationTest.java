package com.thiru.wealthlens.taxengine.rules.cap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * A percentage of a base chosen by population: Table I Sl. 2(a)'s bands (S4 r.15(2); Rules 1962 r.3(1); D-258) —
 * 10% "exceeding forty lakhs", 7.5% "exceeding fifteen lakhs but not exceeding forty lakhs", 5% "other areas" — on a
 * salary of 12,00,000.
 */
class PercentOfBaseByPopulationTest {

    private static final BaseId SALARY = new BaseId("SALARY");
    private static final BaseId POPULATION = new BaseId("POPULATION");
    private static final PercentOfBaseByPopulation TABLE_I = new PercentOfBaseByPopulation(SALARY, POPULATION,
            List.of(new PercentOfBaseByPopulation.Band(1_500_000, Rate.percent("7.5")),
                    new PercentOfBaseByPopulation.Band(4_000_000, Rate.percent("10"))),
            Rate.percent("5"));

    private static CapResult forPopulation(int people) {
        return TABLE_I.apply(new CapContext(Map.of(SALARY, Money.of("1200000")), Map.of(), Map.of(POPULATION, people)));
    }

    @Test
    void apply_whenThePopulationIsExactlyFortyLakh_takesTheMiddleBand() {
        // When — forty lakh does not exceed forty lakh: 7.5% × 12,00,000 = 90,000
        CapResult result = forPopulation(4_000_000);

        // Then
        assertThat(result.value()).isEqualTo(Money.of("90000"));
        assertThat(result.limitedBy()).contains("population of 4000000").contains("exceeding 1500000");
    }

    @Test
    void apply_whenThePopulationExceedsFortyLakh_takesTheTopBand() {
        // When — 10% × 12,00,000 = 1,20,000
        CapResult result = forPopulation(4_000_001);

        // Then
        assertThat(result.value()).isEqualTo(Money.of("120000"));
    }

    @Test
    void apply_whenThePopulationIsExactlyFifteenLakh_takesTheOtherwisePercentage() {
        // When — fifteen lakh does not exceed fifteen lakh: 5% × 12,00,000 = 60,000
        CapResult result = forPopulation(1_500_000);

        // Then
        assertThat(result.value()).isEqualTo(Money.of("60000"));
        assertThat(result.limitedBy()).contains("exceeding no band");
    }

    @Test
    void apply_whenThePopulationExceedsFifteenLakh_takesTheMiddleBand() {
        // When — 7.5% × 12,00,000 = 90,000
        CapResult result = forPopulation(1_500_001);

        // Then
        assertThat(result.value()).isEqualTo(Money.of("90000"));
    }

    @Test
    void apply_whenThePopulationIsNegative_isRefused() {
        assertThatThrownBy(() -> forPopulation(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void construct_whenTwoBandsShareAThreshold_isRefused() {
        assertThatThrownBy(() -> new PercentOfBaseByPopulation(SALARY, POPULATION, List.of(
                        new PercentOfBaseByPopulation.Band(4_000_000, Rate.percent("10")),
                        new PercentOfBaseByPopulation.Band(4_000_000, Rate.percent("7.5"))), Rate.percent("5")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("4000000");
    }

    @Test
    void construct_whenThereAreNoBands_isRefused() {
        assertThatThrownBy(() -> new PercentOfBaseByPopulation(SALARY, POPULATION, List.of(), Rate.percent("5")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void band_whenTheThresholdIsNegative_isRefused() {
        assertThatThrownBy(() -> new PercentOfBaseByPopulation.Band(-1, Rate.percent("5")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
