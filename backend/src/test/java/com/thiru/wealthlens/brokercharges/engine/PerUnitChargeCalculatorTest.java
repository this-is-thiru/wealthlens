package com.thiru.wealthlens.brokercharges.engine;

import static com.thiru.wealthlens.brokercharges.engine.ChargeFixtures.rule;
import static com.thiru.wealthlens.brokercharges.engine.ChargeFixtures.trade;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.brokercharges.dto.enums.ChargeBasis;
import com.thiru.wealthlens.brokercharges.entity.ChargeRule;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** An amount per share. */
class PerUnitChargeCalculatorTest {

    private final PerUnitChargeCalculator calculator = new PerUnitChargeCalculator();

    @Test
    void basis_isPerUnit() {
        assertThat(calculator.basis()).isEqualTo(ChargeBasis.PER_UNIT);
    }

    @ParameterizedTest(name = "{0} per unit over 100 units is {1}")
    @CsvSource({
        "0.05, 5.00",
        "0.0,  0.00",
        "1.5,  150.00",
    })
    void compute_isTheAmountTimesTheQuantity(double perUnit, String expected) {
        // Given — the fixture trades 100 units
        ChargeRule rule = rule("SEBI_FEE", ChargeBasis.PER_UNIT);
        rule.setPerUnitAmount(perUnit);

        // When / Then
        assertThat(calculator.compute(rule, trade(), new ChargeAccumulator()))
                .isEqualByComparingTo(expected);
    }

    @Test
    void compute_whenTheAmountIsAbsent_isZero() {
        ChargeRule rule = rule("SEBI_FEE", ChargeBasis.PER_UNIT);
        rule.setPerUnitAmount(null);

        assertThat(calculator.compute(rule, trade(), new ChargeAccumulator()))
                .isEqualByComparingTo("0");
    }

    @Test
    void compute_whenTheAmountIsNegative_isRejected() {
        ChargeRule rule = rule("SEBI_FEE", ChargeBasis.PER_UNIT);
        rule.setPerUnitAmount(-0.05);

        assertThatThrownBy(() -> calculator.compute(rule, trade(), new ChargeAccumulator()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("SEBI_FEE");
    }
}
