package com.thiru.wealthlens.brokercharges.engine;

import static com.thiru.wealthlens.brokercharges.engine.ChargeFixtures.rule;
import static com.thiru.wealthlens.brokercharges.engine.ChargeFixtures.trade;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.brokercharges.dto.enums.ChargeBasis;
import com.thiru.wealthlens.brokercharges.entity.ChargeRule;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import org.junit.jupiter.api.Test;

/** A fixed amount per chargeable event, whatever the trade's size. */
class FlatChargeCalculatorTest {

    private final FlatChargeCalculator calculator = new FlatChargeCalculator();

    @Test
    void basis_isFlat() {
        assertThat(calculator.basis()).isEqualTo(ChargeBasis.FLAT);
    }

    @Test
    void compute_isTheFlatAmount() {
        ChargeRule rule = rule("BROKERAGE", ChargeBasis.FLAT);
        rule.setFlatAmount(20.0);

        assertThat(calculator.compute(rule, trade(), new ChargeAccumulator()))
                .isEqualByComparingTo("20.00");
    }

    @Test
    void compute_isIndependentOfTradeSize() {
        // Given — the discount broker's whole proposition: one price whatever the trade
        ChargeRule rule = rule("BROKERAGE", ChargeBasis.FLAT);
        rule.setFlatAmount(20.0);

        // When / Then
        assertThat(calculator.compute(rule, trade(1_000.0), new ChargeAccumulator()))
                .isEqualByComparingTo(calculator.compute(rule, trade(10_000_000.0), new ChargeAccumulator()));
    }

    @Test
    void compute_whenTheAmountIsZero_isZero() {
        // Given — a genuine rate on a delivery card, not a missing value
        ChargeRule rule = rule("BROKERAGE", ChargeBasis.FLAT);
        rule.setFlatAmount(0.0);

        assertThat(calculator.compute(rule, trade(), new ChargeAccumulator()))
                .isEqualByComparingTo("0");
    }

    @Test
    void compute_whenTheAmountIsAbsent_isZero() {
        ChargeRule rule = rule("BROKERAGE", ChargeBasis.FLAT);
        rule.setFlatAmount(null);

        assertThat(calculator.compute(rule, trade(), new ChargeAccumulator()))
                .isEqualByComparingTo("0");
    }

    @Test
    void compute_whenTheAmountIsNegative_isRejected() {
        ChargeRule rule = rule("BROKERAGE", ChargeBasis.FLAT);
        rule.setFlatAmount(-20.0);

        assertThatThrownBy(() -> calculator.compute(rule, trade(), new ChargeAccumulator()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("BROKERAGE");
    }
}
