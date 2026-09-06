package com.thiru.wealthlens.brokercharges.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.brokercharges.dto.context.ChargeContext;
import com.thiru.wealthlens.brokercharges.dto.context.LotSlice;
import com.thiru.wealthlens.brokercharges.dto.enums.AmountBasis;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeBasis;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeCategory;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeEvent;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeRuleSource;
import com.thiru.wealthlens.brokercharges.dto.enums.RoundingPolicy;
import com.thiru.wealthlens.brokercharges.dto.enums.TradeSegment;
import com.thiru.wealthlens.brokercharges.entity.ChargeLine;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The escape hatch that keeps a new charge a data change.
 *
 * <p>Owned by this module outright — it shares no code with the tax planning evaluator, returns
 * money to two decimals rather than whole rupees, and exposes charge-specific variables.
 */
class ChargeFormulaEvaluatorTest {

    private final ChargeFormulaEvaluator evaluator = new ChargeFormulaEvaluator();

    @Test
    void evaluate_readsTurnoverFromTheContext() {
        ChargeContext context = context(Map.of());

        assertThat(evaluator.evaluate("#turnover * 0.01", context, new ChargeAccumulator()))
                .isEqualByComparingTo("1000.00");
    }

    @Test
    void evaluate_readsQuantityAndPrice() {
        ChargeContext context = context(Map.of());

        assertThat(evaluator.evaluate("#quantity * 2", context, new ChargeAccumulator()))
                .isEqualByComparingTo("200");
        assertThat(evaluator.evaluate("#price", context, new ChargeAccumulator()))
                .isEqualByComparingTo("1000");
    }

    @Test
    void evaluate_readsArbitraryAttributes() {
        // Given — instrument and user facts arrive through attributes, so a broker-owned rule can
        // depend on a scheme attribute without a model change
        ChargeContext context = context(Map.of("holdingDays", 200L, "equityOriented", true));

        // When / Then
        assertThat(evaluator.evaluate("#holdingDays", context, new ChargeAccumulator()))
                .isEqualByComparingTo("200");
    }

    @Test
    void evaluate_readsTheRunningAccumulator() {
        // Given — a formula may build on charges already applied
        ChargeAccumulator accumulator = new ChargeAccumulator();
        accumulator.add(line("BROKERAGE", 20.00));

        // When
        BigDecimal amount = evaluator.evaluate("#charges['BROKERAGE'] * 0.18", context(Map.of()), accumulator);

        // Then — returned unrounded. Rounding is applied once by the engine after every other
        // modifier; rounding here as well would compound the error. SpEL computes in double, so
        // this is 3.5999999999999996 before the engine takes it to paise.
        assertThat(ChargeRounding.apply(amount, RoundingPolicy.HALF_UP_2)).isEqualByComparingTo("3.60");
    }

    @Test
    void evaluate_whenACodeWasNotCharged_readsAsZero() {
        assertThat(evaluator.evaluate("#charges['DP']", context(Map.of()), new ChargeAccumulator()))
                .isEqualByComparingTo("0");
    }

    @Test
    void matches_evaluatesAPredicate() {
        // Given — the shape a mutual fund exit load takes
        ChargeContext held200 = context(Map.of("holdingDays", 200L));
        ChargeContext held500 = context(Map.of("holdingDays", 500L));

        // When / Then
        assertThat(evaluator.matches("#holdingDays < 365", held200, new ChargeAccumulator())).isTrue();
        assertThat(evaluator.matches("#holdingDays < 365", held500, new ChargeAccumulator())).isFalse();
    }

    @Test
    void matches_whenPredicateIsAbsent_theRuleApplies() {
        // Given — eligibility is optional; omitting it must not silently disable a rule
        assertThat(evaluator.matches(null, context(Map.of()), new ChargeAccumulator())).isTrue();
        assertThat(evaluator.matches("  ", context(Map.of()), new ChargeAccumulator())).isTrue();
    }

    @Test
    void matches_combinesContextAndAttributes() {
        // Given — the mutual fund distributor fee: the broker sets the amount, the scheme's plan
        // type decides whether it can apply, and the user's history decides which rate
        ChargeContext context = context(Map.of("planType", "REGULAR", "firstTimeInvestor", true));

        // When / Then
        assertThat(evaluator.matches(
                "#planType == 'REGULAR' and #turnover >= 10000 and #firstTimeInvestor",
                context, new ChargeAccumulator())).isTrue();
    }

    @Test
    void matches_whenAVariableIsAbsent_isFalseRatherThanThrowing() {
        // Given — a missing instrument profile leaves equityOriented unset. The rule must not apply,
        // and must not blow up the evaluation; the omission is recorded as a resolution instead.
        assertThat(evaluator.matches("#equityOriented == true", context(Map.of()), new ChargeAccumulator()))
                .isFalse();
    }

    @Test
    void evaluate_whenExpressionIsAbsent_isZero() {
        assertThat(evaluator.evaluate(null, context(Map.of()), new ChargeAccumulator()))
                .isEqualByComparingTo("0");
        assertThat(evaluator.evaluate("   ", context(Map.of()), new ChargeAccumulator()))
                .isEqualByComparingTo("0");
    }

    @Test
    void evaluate_whenExpressionIsMalformed_isRejected() {
        assertThatThrownBy(() -> evaluator.evaluate("#turnover *", context(Map.of()), new ChargeAccumulator()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("expression");
    }

    @Test
    void evaluate_whenExpressionYieldsANegativeAmount_isRejected() {
        // A charge that pays the user is a rate-card error, not a discount
        assertThatThrownBy(() -> evaluator.evaluate("-1", context(Map.of()), new ChargeAccumulator()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void validate_acceptsAParseableExpression() {
        evaluator.validate("#turnover * 0.01");
        evaluator.validate("#holdingDays < 365");
    }

    @Test
    void validate_rejectsAnUnparseableExpression() {
        // Rate cards are data, so nothing else checks them before a trade is priced
        assertThatThrownBy(() -> evaluator.validate("#turnover *"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("expression");
    }

    @Test
    void referencedVariables_areExtractedForValidation() {
        // Given — a typo such as #equityOrientd parses cleanly, evaluates to null, and silently
        // disables its rule forever. The validator compares these names against a known vocabulary.
        assertThat(evaluator.referencedVariables("#planType == 'REGULAR' and #turnover >= 10000"))
                .containsExactlyInAnyOrder("planType", "turnover");
        assertThat(evaluator.referencedVariables(null)).isEmpty();
    }

    @Test
    void evaluate_readsLotSizeAndSide() {
        // Given — both are part of the documented vocabulary. A derivatives rule prices on lot
        // size, and a rule that differs between buying and selling reads the side.
        ChargeContext context = context(Map.of());

        // When / Then
        assertThat(evaluator.evaluate("#lotSize", context, new ChargeAccumulator()))
                .isEqualByComparingTo("1");
        assertThat(evaluator.matches("#side == 'SELL'", context, new ChargeAccumulator())).isTrue();
        assertThat(evaluator.matches("#side == 'BUY'", context, new ChargeAccumulator())).isFalse();
    }

    @Test
    void evaluate_whenTheExpressionYieldsNothing_isZeroRatherThanNull() {
        // Given — a formula naming a fact this context does not carry. It must contribute nothing,
        // the same way an absent expression does, rather than returning null into the arithmetic.
        assertThat(evaluator.evaluate("#unknownFact", context(Map.of()), new ChargeAccumulator()))
                .isEqualByComparingTo("0");
    }

    private static ChargeContext context(Map<String, Object> attributes) {
        return new ChargeContext(
                "txn-1", "ord-1", "RELIANCE", "self", BrokerName.ZERODHA, AssetType.EQUITY,
                TradeSegment.DELIVERY, "NSE", null, ChargeEvent.SELL, LocalDate.of(2025, 6, 1),
                null, 100, 1000, 1,
                Map.of(AmountBasis.TURNOVER, 100000.0),
                List.of(new LotSlice(100, LocalDate.of(2024, 1, 1), 900)),
                attributes);
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
