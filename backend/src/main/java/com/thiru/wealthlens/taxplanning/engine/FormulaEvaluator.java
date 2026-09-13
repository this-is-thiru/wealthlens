package com.thiru.wealthlens.taxplanning.engine;

import com.thiru.wealthlens.shared.util.expression.SafeExpressions;
import java.util.Map;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;

/**
 * Evaluates the allowance and exemption formulas carried by tax policy data.
 *
 * <p>Owned by this module outright. The charges engine solves a similar problem with its own
 * evaluator; neither depends on the other, and the contracts differ — this returns whole rupees
 * where that returns money to two decimals, and the vocabularies are entirely different. Only the
 * safety floor is shared, through {@link SafeExpressions}.
 *
 * <p><b>Formulas here are data.</b> {@code AllowanceCatalogueEntity.limitFormula} is written
 * through {@code PUT /tax-planning/admin/allowances/{code}} and seeded from JSON, so an expression
 * evaluated here may have arrived over the wire. It is therefore evaluated in a read-only context
 * that cannot resolve a type, a bean or a constructor — previously it was
 * {@code StandardEvaluationContext}, under which {@code T(java.lang.Runtime).getRuntime().exec(…)}
 * was a valid allowance limit.
 */
@Component
public class FormulaEvaluator {

    private final ExpressionParser parser = new SpelExpressionParser();

    /**
     * The formula's value, or zero when there is none.
     *
     * <p>The overload that accepted a caller-supplied {@code EvaluationContext} is gone. It had no
     * callers, and it was the hole this class could not close for itself: a caller passing a
     * {@code StandardEvaluationContext} would have reopened everything below.
     */
    public long evaluate(String formula, Map<String, Object> variables) {
        if (formula == null || formula.isBlank()) {
            return 0L;
        }
        SafeExpressions.reject(formula);
        SimpleEvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding().build();
        variables.forEach(context::setVariable);
        return evaluate(formula, context);
    }

    /** The house-rent vocabulary, which is fixed rather than policy-supplied. */
    public long evaluate(String formula, Long basic, Long da, Long rentPaid, Long hraReceived) {
        return evaluate(formula, Map.of(
                "basic", basic != null ? basic : 0L,
                "da", da != null ? da : 0L,
                "rentPaid", rentPaid != null ? rentPaid : 0L,
                "hraReceived", hraReceived != null ? hraReceived : 0L));
    }

    private long evaluate(String formula, EvaluationContext context) {
        Long value = parser.parseExpression(formula).getValue(context, Long.class);
        return value == null ? 0L : value;
    }
}
