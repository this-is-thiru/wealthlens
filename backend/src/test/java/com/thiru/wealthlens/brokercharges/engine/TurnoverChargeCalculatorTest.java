package com.thiru.wealthlens.brokercharges.engine;

import static com.thiru.wealthlens.brokercharges.engine.ChargeFixtures.rule;
import static com.thiru.wealthlens.brokercharges.engine.ChargeFixtures.trade;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.brokercharges.dto.enums.AmountBasis;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeBasis;
import com.thiru.wealthlens.brokercharges.entity.ChargeRule;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import com.thiru.wealthlens.testsupport.LogCapture;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * A percentage of the context amount the rule names.
 *
 * <p>Floors, caps, aggregation against a flat fee and rounding are deliberately absent here. They
 * are the engine's, applied once in a fixed order (ADR-15), and asserted in {@code ChargeEngineTest}
 * — a calculator that applied them too would round twice.
 */
class TurnoverChargeCalculatorTest {

    private final TurnoverChargeCalculator calculator = new TurnoverChargeCalculator();

    @Test
    void basis_isTurnover() {
        assertThat(calculator.basis()).isEqualTo(ChargeBasis.TURNOVER);
    }

    @ParameterizedTest(name = "{0}% of {1} is {2}")
    @CsvSource({
        "0.1,     100000, 100.00",
        "0.0,     100000, 0.00",
        "0.1,     0,      0.00",
        "0.03,    20000,  6.00",
        "0.0001,  100000, 0.10",
        "100.0,   100000, 100000.00",
    })
    void compute_isThePercentageOfTheBase(double rate, double turnover, String expected) {
        // Given
        ChargeRule rule = rule("STT", ChargeBasis.TURNOVER);
        rule.setRate(rate);

        // When / Then
        assertThat(calculator.compute(rule, trade(turnover), new ChargeAccumulator()))
                .isEqualByComparingTo(expected);
    }

    @Test
    void compute_returnsTheUnroundedAmount() {
        // Given — 0.0297% of 100000 is 29.7, and 0.1234% is 123.4. The engine rounds; rounding here
        // as well would apply the policy twice and drift against a real contract note.
        ChargeRule rule = rule("EXCHANGE_TXN", ChargeBasis.TURNOVER);
        rule.setRate(0.00123456);

        // When / Then — every digit survives
        assertThat(calculator.compute(rule, trade(100000), new ChargeAccumulator()))
                .isEqualByComparingTo("1.23456");
    }

    @Test
    void compute_whenRateIsAbsent_isZero() {
        // Given — a rule of this basis with no rate is a card defect, but it must not be priced as
        // if the rate were something else
        ChargeRule rule = rule("STT", ChargeBasis.TURNOVER);
        rule.setRate(null);

        // When / Then
        assertThat(calculator.compute(rule, trade(), new ChargeAccumulator()))
                .isEqualByComparingTo("0");
    }

    @Test
    void compute_pricesOnTheAmountBasisTheRuleNames() {
        // Given — an option is priced on premium, not on turnover. Phase A seeds nothing that does
        // this, but the field is what keeps adding options a rule change rather than a migration.
        ChargeRule rule = rule("STT", ChargeBasis.TURNOVER);
        rule.setAmountBasis(AmountBasis.PREMIUM);
        rule.setRate(0.05);

        Map<AmountBasis, Double> amounts = new EnumMap<>(AmountBasis.class);
        amounts.put(AmountBasis.TURNOVER, 100000.0);
        amounts.put(AmountBasis.PREMIUM, 20000.0);

        // When / Then — 0.05% of the premium, not of the turnover
        assertThat(calculator.compute(rule, trade(amounts, Map.of(), List.of()), new ChargeAccumulator()))
                .isEqualByComparingTo("10.00");
    }

    @Test
    void compute_whenTheRuleNamesAnAmountTheContextDoesNotCarry_isZeroAndWarns() {
        // Given — the caller did not supply the premium this rule prices on
        ChargeRule rule = rule("STT", ChargeBasis.TURNOVER);
        rule.setAmountBasis(AmountBasis.PREMIUM);
        rule.setRate(0.05);

        // When
        try (LogCapture logs = LogCapture.on(TurnoverChargeCalculator.class)) {
            BigDecimal amount = calculator.compute(rule, trade(), new ChargeAccumulator());

            // Then — zero rather than silently falling back to turnover, which would charge 0.05%
            // of an unrelated number. Asserted with the warning, because a silent zero is the exact
            // failure this design exists to prevent.
            assertThat(amount).isEqualByComparingTo("0");
            assertThat(logs.warnings()).singleElement().asString().contains("STT").contains("PREMIUM");
        }
    }

    @Test
    void compute_whenTheContextCarriesTheAmount_doesNotWarn() {
        // Given — the ordinary case must stay quiet, or the warning means nothing
        ChargeRule rule = rule("STT", ChargeBasis.TURNOVER);
        rule.setRate(0.1);

        // When / Then
        try (LogCapture logs = LogCapture.on(TurnoverChargeCalculator.class)) {
            calculator.compute(rule, trade(), new ChargeAccumulator());
            assertThat(logs.warnings()).isEmpty();
        }
    }

    @Test
    void compute_whenTheRateIsZero_doesNotWarnAboutAMissingAmount() {
        // Given — a rule that charges nothing has nothing to price, so the amount it would have
        // priced on being absent is not worth reporting
        ChargeRule rule = rule("STT", ChargeBasis.TURNOVER);
        rule.setAmountBasis(AmountBasis.PREMIUM);
        rule.setRate(0.0);

        // When / Then
        try (LogCapture logs = LogCapture.on(TurnoverChargeCalculator.class)) {
            assertThat(calculator.compute(rule, trade(), new ChargeAccumulator())).isEqualByComparingTo("0");
            assertThat(logs.warnings()).isEmpty();
        }
    }

    @Test
    void compute_whenTheRuleNamesNoAmountBasis_pricesOnTurnover() {
        // Given
        ChargeRule rule = rule("STT", ChargeBasis.TURNOVER);
        rule.setAmountBasis(null);
        rule.setRate(0.1);

        // When / Then
        assertThat(calculator.compute(rule, trade(100000), new ChargeAccumulator()))
                .isEqualByComparingTo("100.00");
    }

    @Test
    void compute_whenTheBaseIsNegative_isRejected() {
        // Given — a negative turnover is a caller defect. Priced, it would produce a charge that
        // pays the user, which no reconciliation would explain.
        ChargeRule rule = rule("STT", ChargeBasis.TURNOVER);
        rule.setRate(0.1);

        // When / Then
        assertThatThrownBy(() -> calculator.compute(rule, trade(-100), new ChargeAccumulator()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("STT");
    }

    @Test
    void compute_whenTheRateIsNegative_isRejected() {
        // Given — a discount expressed as a negative rate is a rate-card error, not a feature
        ChargeRule rule = rule("STT", ChargeBasis.TURNOVER);
        rule.setRate(-0.1);

        // When / Then
        assertThatThrownBy(() -> calculator.compute(rule, trade(), new ChargeAccumulator()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("STT");
    }
}
