package com.thiru.wealthlens.brokercharges.engine;

import static com.thiru.wealthlens.brokercharges.engine.ChargeFixtures.rule;
import static com.thiru.wealthlens.brokercharges.engine.ChargeFixtures.trade;
import static com.thiru.wealthlens.brokercharges.engine.ChargeFixtures.tradeWithAttributes;
import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.brokercharges.dto.enums.ChargeBasis;
import com.thiru.wealthlens.brokercharges.dto.enums.SlabBandBasis;
import com.thiru.wealthlens.brokercharges.entity.ChargeRule;
import com.thiru.wealthlens.brokercharges.entity.ChargeSlab;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * A tiered charge: the band is selected by one quantity, and the rate within it applies to another.
 *
 * <p>Those two are not the same thing and conflating them is the trap. An exit load banded by
 * holding period charges a percentage <em>of the redemption</em>, not of the number of days.
 */
class SlabChargeCalculatorTest {

    private final SlabChargeCalculator calculator = new SlabChargeCalculator();

    @Test
    void basis_isSlab() {
        assertThat(calculator.basis()).isEqualTo(ChargeBasis.SLAB);
    }

    @ParameterizedTest(name = "turnover {0} falls in the band charging {1}")
    @CsvSource({
        "5000,   50.00",
        "50000,  100.00",
        "500000, 250.00",
    })
    void compute_selectsTheBandTheValueFallsIn(double turnover, String expected) {
        // Given — flat amounts per band, so the selection is what is under test
        ChargeRule rule = bandedRule(
                slab(0.0, 10000.0, null, 50.0),
                slab(10000.0, 100000.0, null, 100.0),
                slab(100000.0, null, null, 250.0));

        // When / Then
        assertThat(calculator.compute(rule, trade(turnover), new ChargeAccumulator()))
                .isEqualByComparingTo(expected);
    }

    @Test
    void compute_treatsTheLowerBoundAsInclusiveAndTheUpperAsExclusive() {
        // Given — the boundary is where a tiered card is most often got wrong
        ChargeRule rule = bandedRule(
                slab(0.0, 10000.0, null, 50.0),
                slab(10000.0, 100000.0, null, 100.0));

        // When / Then — 10000 belongs to the second band, not the first
        assertThat(calculator.compute(rule, trade(9999.99), new ChargeAccumulator()))
                .isEqualByComparingTo("50.00");
        assertThat(calculator.compute(rule, trade(10000.0), new ChargeAccumulator()))
                .isEqualByComparingTo("100.00");
    }

    @Test
    void compute_whenTheBandIsRateBased_appliesItToTheRulesAmountBasis() {
        // Given — the band is chosen by turnover and the rate applies to turnover too
        ChargeRule rule = bandedRule(slab(0.0, null, 0.03, null));

        // When / Then — 0.03% of 100000
        assertThat(calculator.compute(rule, trade(100000), new ChargeAccumulator()))
                .isEqualByComparingTo("30.0000");
    }

    @Test
    void compute_whenBandedByHoldingDays_appliesTheRateToTheTradeNotToTheDays() {
        // Given — an exit load of 1% for the first year. Banding by holding period and charging a
        // percentage of that period would produce a charge measured in days.
        ChargeRule rule = bandedRule(slab(0.0, 365.0, 1.0, null), slab(365.0, null, 0.0, null));
        rule.setSlabBandBasis(SlabBandBasis.HOLDING_DAYS);

        // When / Then — 1% of the 100000 redeemed, for a lot held 30 days
        assertThat(calculator.compute(
                        rule, tradeWithAttributes(Map.of("holdingDays", 30L)), new ChargeAccumulator()))
                .isEqualByComparingTo("1000.00");
        assertThat(calculator.compute(
                        rule, tradeWithAttributes(Map.of("holdingDays", 400L)), new ChargeAccumulator()))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void compute_whenBandedByQuantity_selectsOnTheNumberOfUnits() {
        // Given — the fixture trades 100 units
        ChargeRule rule = bandedRule(slab(0.0, 100.0, null, 10.0), slab(100.0, null, null, 25.0));
        rule.setSlabBandBasis(SlabBandBasis.QUANTITY);

        // When / Then
        assertThat(calculator.compute(rule, trade(), new ChargeAccumulator()))
                .isEqualByComparingTo("25.00");
    }

    @Test
    void compute_whenNoBandMatches_isZero() {
        // Given — a card whose bands do not cover this trade. Charging the nearest band would be a
        // guess; the gap belongs in the validator, not in an invented amount.
        ChargeRule rule = bandedRule(slab(1000000.0, null, null, 500.0));

        assertThat(calculator.compute(rule, trade(100000), new ChargeAccumulator()))
                .isEqualByComparingTo("0");
    }

    @Test
    void compute_whenTheRuleHasNoSlabs_isZero() {
        ChargeRule rule = rule("BROKERAGE", ChargeBasis.SLAB);
        rule.setSlabs(null);

        assertThat(calculator.compute(rule, trade(), new ChargeAccumulator())).isEqualByComparingTo("0");
    }

    @Test
    void compute_whenTheBandNamesNoLowerBound_treatsItAsUnbounded() {
        // Given — an omitted lower bound means "from zero", which is how a first band is usually
        // written on a card
        ChargeRule rule = bandedRule(slab(null, 10000.0, null, 50.0));

        assertThat(calculator.compute(rule, trade(5000), new ChargeAccumulator()))
                .isEqualByComparingTo("50.00");
    }

    @Test
    void compute_whenTheRuleNamesNoBandBasis_bandsOnTurnover() {
        ChargeRule rule = bandedRule(slab(0.0, 10000.0, null, 50.0), slab(10000.0, null, null, 100.0));
        rule.setSlabBandBasis(null);

        assertThat(calculator.compute(rule, trade(50000), new ChargeAccumulator()))
                .isEqualByComparingTo("100.00");
    }

    private static ChargeRule bandedRule(ChargeSlab... slabs) {
        ChargeRule rule = rule("BROKERAGE", ChargeBasis.SLAB);
        rule.setSlabBandBasis(SlabBandBasis.TURNOVER);
        rule.setSlabs(List.of(slabs));
        return rule;
    }

    private static ChargeSlab slab(Double from, Double to, Double rate, Double flatAmount) {
        return new ChargeSlab(from, to, rate, flatAmount);
    }
}
