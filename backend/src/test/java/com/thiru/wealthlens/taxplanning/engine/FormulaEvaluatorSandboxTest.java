package com.thiru.wealthlens.taxplanning.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.thiru.wealthlens.shared.exception.BadRequestException;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * What an allowance limit formula is allowed to do.
 *
 * <p>{@code AllowanceCatalogueEntity.limitFormula} is written through
 * {@code PUT /tax-planning/admin/allowances/{code}} and evaluated by this class. It had the same
 * unrestricted evaluation context as the charges engine did — found only because the charges one
 * was looked at first, which is the argument for the shared guard the fix uses.
 */
@Tag("unit")
class FormulaEvaluatorSandboxTest {

    private final FormulaEvaluator evaluator = new FormulaEvaluator();

    @ParameterizedTest
    @ValueSource(strings = {
            "T(java.lang.Runtime).getRuntime().availableProcessors()",
            "T(java.lang.System).getProperty('user.name').length()",
            "new java.lang.String('x').length()"
    })
    @DisplayName("a formula reaching outside the tax vocabulary cannot be evaluated")
    void evaluate_whenFormulaReachesOutsideTheVocabulary_refuses(String hostile) {
        assertThrows(BadRequestException.class, () -> evaluator.evaluate(hostile, Map.of("basic", 100L)));
    }

    /** The real formulas must keep working, or the sandbox has broken tax computation. */
    @Test
    @DisplayName("arithmetic over the supplied variables still evaluates")
    void evaluate_whenFormulaUsesTheVariables_stillWorks() {
        assertEquals(50000L, evaluator.evaluate("#basic * 10 / 100", Map.of("basic", 500000L)));
    }

    @Test
    @DisplayName("the house-rent formula still evaluates")
    void evaluate_whenHraFormula_stillWorks() {
        assertEquals(40000L, evaluator.evaluate("#rentPaid - (#basic * 10 / 100)",
                Map.of("rentPaid", 90000L, "basic", 500000L)));
    }

    @Test
    @DisplayName("a blank formula is zero rather than a failure")
    void evaluate_whenFormulaIsBlank_isZero() {
        assertEquals(0L, evaluator.evaluate("  ", Map.of()));
    }
}
