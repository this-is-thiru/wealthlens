package com.thiru.wealthlens.brokercharges.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thiru.wealthlens.brokercharges.dto.context.ChargeContext;
import com.thiru.wealthlens.brokercharges.dto.enums.AmountBasis;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeEvent;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * What a rate card is allowed to do.
 *
 * <p>A rate card is data — it arrives through {@code POST /charge-schedules} or straight into
 * Mongo — and its formulas are evaluated inside this application's JVM, which holds the database
 * credentials and the JWT signing key. Evaluating them with SpEL's unrestricted context turned
 * "may edit rate cards" into "may run code", silently: the validator inspects {@code #variable}
 * names, and an expression reaching {@code T(java.lang.Runtime)} contains none.
 *
 * <p>These are the cases that must stay refused. The arithmetic tests beside them in
 * {@code ChargeFormulaEvaluatorTest} are what must keep working.
 */
@Tag("unit")
class ChargeFormulaEvaluatorSandboxTest {

    private final ChargeFormulaEvaluator evaluator = new ChargeFormulaEvaluator();

    /** Each of these reaches outside the charge vocabulary. None contains a '#'. */
    @ParameterizedTest
    @ValueSource(strings = {
            "T(java.lang.Runtime).getRuntime().availableProcessors()",
            "T(java.lang.System).getProperty('user.name').length()",
            "new java.lang.String('x').length()",
            "T(java.lang.Class).forName('java.lang.Runtime').hashCode()"
    })
    @DisplayName("an expression reaching outside the charge vocabulary cannot be evaluated")
    void evaluate_whenExpressionReachesOutsideTheVocabulary_refuses(String hostile) {
        assertThrows(BadRequestException.class,
                () -> evaluator.evaluate(hostile, context(), new ChargeAccumulator()));
    }

    /**
     * Refused when the card is written, not when a trade is priced.
     *
     * <p>Validation is the only thing standing between a hostile card and the next trade that
     * resolves to it, and a card that parses is a card that will be evaluated.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "T(java.lang.Runtime).getRuntime()",
            "new java.io.File('/')",
            "@someBean.toString()"
    })
    @DisplayName("a card carrying such an expression is rejected at write time")
    void validate_whenExpressionReachesOutsideTheVocabulary_refuses(String hostile) {
        BadRequestException thrown = assertThrows(BadRequestException.class, () -> evaluator.validate(hostile));
        assertTrue(thrown.getMessage().contains(hostile), thrown.getMessage());
    }

    /** The vocabulary itself must keep working, or the sandbox has broken the engine. */
    @Test
    @DisplayName("arithmetic over the published variables still evaluates")
    void evaluate_whenExpressionUsesTheVocabulary_stillWorks() {
        assertEquals(new BigDecimal("100.00").compareTo(
                evaluator.evaluate("#turnover * 0.01", context(), new ChargeAccumulator())), 0);
    }

    @Test
    @DisplayName("the charges map still reads back what has already been applied")
    void evaluate_whenExpressionReadsTheAccumulator_stillWorks() {
        ChargeAccumulator accumulator = new ChargeAccumulator();
        accumulator.add(line("BROKERAGE", 100.0));

        assertEquals(0, new BigDecimal("18.000").compareTo(
                evaluator.evaluate("#charges['BROKERAGE'] * 0.18", context(), accumulator)));
    }

    @Test
    @DisplayName("a charge the event did not attract still reads as zero, not as a failure")
    void evaluate_whenChargeCodeIsAbsent_readsAsZero() {
        assertEquals(0, BigDecimal.ZERO.compareTo(
                evaluator.evaluate("#charges['DP']", context(), new ChargeAccumulator())));
    }

    @Test
    @DisplayName("an eligibility predicate over instrument attributes still holds")
    void matches_whenPredicateUsesAttributes_stillWorks() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("equityOriented", true);
        attributes.put("holdingDays", 3L);

        assertTrue(evaluator.matches("#equityOriented == true and #holdingDays < 7",
                context(attributes), new ChargeAccumulator()));
    }

    private static com.thiru.wealthlens.brokercharges.entity.ChargeLine line(String code, double amount) {
        var chargeLine = new com.thiru.wealthlens.brokercharges.entity.ChargeLine();
        chargeLine.setCode(code);
        chargeLine.setAmount(amount);
        return chargeLine;
    }

    private static ChargeContext context() {
        return context(new HashMap<>());
    }

    private static ChargeContext context(Map<String, Object> attributes) {
        return new ChargeContext("test@example.com", "txn-1", "order-1", "INFY", "holder", null, null, null,
                "NSE", null, ChargeEvent.BUY, LocalDate.of(2025, 4, 1), null, 10, 1000, 1,
                Map.of(AmountBasis.TURNOVER, 10000.0), List.of(), attributes);
    }
}
