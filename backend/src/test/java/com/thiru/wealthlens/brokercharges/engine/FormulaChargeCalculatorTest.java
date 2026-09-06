package com.thiru.wealthlens.brokercharges.engine;

import static com.thiru.wealthlens.brokercharges.engine.ChargeFixtures.rule;
import static com.thiru.wealthlens.brokercharges.engine.ChargeFixtures.trade;
import static com.thiru.wealthlens.brokercharges.engine.ChargeFixtures.tradeWithAttributes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.brokercharges.dto.enums.ChargeBasis;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeCategory;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeRuleSource;
import com.thiru.wealthlens.brokercharges.entity.ChargeLine;
import com.thiru.wealthlens.brokercharges.entity.ChargeRule;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * An expression over the trade and the charges applied so far.
 *
 * <p>The escape hatch that keeps a new charge a data change. Mutual fund exit load is the motivating
 * case: its amount depends on how long the units were held, which no fixed field expresses.
 */
class FormulaChargeCalculatorTest {

    private final FormulaChargeCalculator calculator = new FormulaChargeCalculator(new ChargeFormulaEvaluator());

    @Test
    void basis_isFormula() {
        assertThat(calculator.basis()).isEqualTo(ChargeBasis.FORMULA);
    }

    @Test
    void compute_evaluatesTheExpressionOverTheTrade() {
        ChargeRule rule = formula("#turnover * 0.01");

        assertThat(calculator.compute(rule, trade(), new ChargeAccumulator()))
                .isEqualByComparingTo("1000.00");
    }

    @Test
    void compute_readsAnAttributeTheContextCarries() {
        // Given — exit load tapering by holding period, which the engine publishes per lot
        ChargeRule rule = formula("#holdingDays < 30 ? #turnover * 0.01 : 0");

        // When / Then
        assertThat(calculator.compute(rule, tradeWithAttributes(Map.of("holdingDays", 10L)), new ChargeAccumulator()))
                .isEqualByComparingTo("1000.00");
        assertThat(calculator.compute(rule, tradeWithAttributes(Map.of("holdingDays", 400L)), new ChargeAccumulator()))
                .isEqualByComparingTo("0");
    }

    @Test
    void compute_readsTheChargesAlreadyApplied() {
        // Given — a charge expressed against another, without being a DERIVED percentage
        ChargeAccumulator accumulator = new ChargeAccumulator();
        accumulator.add(line("BROKERAGE", 20.00));

        ChargeRule rule = formula("#charges['BROKERAGE'] * 2");

        // When / Then
        assertThat(calculator.compute(rule, trade(), accumulator)).isEqualByComparingTo("40.00");
    }

    @Test
    void compute_whenTheFormulaNamesAChargeThatWasNotLevied_readsItAsZero() {
        ChargeRule rule = formula("#charges['DP'] * 2");

        assertThat(calculator.compute(rule, trade(), new ChargeAccumulator())).isEqualByComparingTo("0");
    }

    @Test
    void compute_whenTheFormulaIsAbsent_isZero() {
        // Given — a rule of this basis with no expression is a card defect, but pricing it as
        // anything other than nothing would be inventing a charge
        ChargeRule rule = rule("EXIT_LOAD", ChargeBasis.FORMULA);
        rule.setFormula(null);

        assertThat(calculator.compute(rule, trade(), new ChargeAccumulator())).isEqualByComparingTo("0");
    }

    @Test
    void compute_whenTheFormulaIsMalformed_isRejected() {
        ChargeRule rule = formula("#turnover *");

        assertThatThrownBy(() -> calculator.compute(rule, trade(), new ChargeAccumulator()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void compute_whenTheFormulaYieldsANegativeAmount_isRejected() {
        // Given — a charge that pays the user is a rate-card error, not a discount
        ChargeRule rule = formula("0 - #turnover");

        assertThatThrownBy(() -> calculator.compute(rule, trade(), new ChargeAccumulator()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void compute_whenTheFormulaNamesAFactTheContextLacks_isZero() {
        // Given — a typo, or a fact only some instruments carry. Zero rather than fatal: the gap is
        // recorded on the computation instead of failing the trade.
        ChargeRule rule = formula("#unknownFact");

        assertThat(calculator.compute(rule, trade(), new ChargeAccumulator())).isEqualByComparingTo("0");
    }

    @Test
    void compute_returnsTheUnroundedAmount() {
        // Given — 100 units divided three ways. The engine rounds once, afterwards; rounding here
        // as well would apply the policy twice.
        ChargeRule rule = formula("#quantity / 3");

        // When
        BigDecimal amount = calculator.compute(rule, trade(), new ChargeAccumulator());

        // Then — every digit the expression produced survives
        assertThat(amount.scale()).isGreaterThan(2);
        assertThat(amount).isGreaterThan(new BigDecimal("33.33")).isLessThan(new BigDecimal("33.34"));
    }

    private static ChargeRule formula(String expression) {
        ChargeRule rule = rule("EXIT_LOAD", ChargeBasis.FORMULA);
        rule.setFormula(expression);
        return rule;
    }

    private static ChargeLine line(String code, double amount) {
        ChargeLine line = new ChargeLine();
        line.setCode(code);
        line.setCategory(ChargeCategory.BROKERAGE);
        line.setBasis(ChargeBasis.FLAT);
        line.setSource(ChargeRuleSource.SCHEDULE);
        line.setAmount(amount);
        return line;
    }
}
