package com.thiru.wealthlens.brokercharges.engine;

import com.thiru.wealthlens.brokercharges.dto.context.ChargeContext;
import com.thiru.wealthlens.brokercharges.dto.enums.AmountBasis;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

/**
 * Evaluates the expressions a rate card may carry: an amount formula, and an eligibility predicate.
 *
 * <p>This is the escape hatch that keeps a new charge a data change. A mutual fund exit load
 * conditioned on holding period, or a distributor fee conditioned on plan type and the user's
 * history, are expressible without touching Java.
 *
 * <p>Owned by this module outright. The tax planning module solves a similar problem with its own
 * evaluator; neither depends on the other, and the contracts differ — this returns money to two
 * decimals where that returns whole rupees, and the exposed vocabulary is entirely different.
 *
 * <h2>Exposed variables</h2>
 * {@code #turnover}, {@code #quantity}, {@code #price}, {@code #lotSize}, {@code #side},
 * every {@link AmountBasis} by lower-cased name, every key of {@code ChargeContext.attributes()},
 * and {@code #charges['CODE']} reading the live accumulator.
 */
@Component
public class ChargeFormulaEvaluator {

    /** Matches a SpEL variable reference, so a validator can check the names against a vocabulary. */
    private static final Pattern VARIABLE = Pattern.compile("#([A-Za-z_][A-Za-z0-9_]*)");

    private final ExpressionParser parser = new SpelExpressionParser();

    /**
     * The amount an expression produces, or zero when there is none.
     *
     * @throws BadRequestException if the expression cannot be parsed, or yields a negative amount —
     *                             a charge that pays the user is a rate-card error, not a discount
     */
    public BigDecimal evaluate(String expression, ChargeContext context, ChargeAccumulator accumulator) {
        if (isBlank(expression)) {
            return BigDecimal.ZERO;
        }

        Number value = read(expression, context, accumulator, Number.class);
        if (value == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal amount = new BigDecimal(value.toString());
        if (amount.signum() < 0) {
            throw new BadRequestException(
                    "Charge expression produced a negative amount: " + expression + " gave " + amount);
        }
        return amount;
    }

    /**
     * Whether a rule's eligibility predicate holds.
     *
     * <p>An absent predicate means the rule applies — omitting one must never silently disable a
     * charge. A predicate referencing a variable the context does not carry is false rather than
     * fatal: a missing instrument profile leaves {@code equityOriented} unset, and that gap is
     * recorded as a resolution on the computation rather than by failing the trade.
     */
    public boolean matches(String predicate, ChargeContext context, ChargeAccumulator accumulator) {
        if (isBlank(predicate)) {
            return true;
        }
        return Boolean.TRUE.equals(read(predicate, context, accumulator, Boolean.class));
    }

    /**
     * Parses an expression without evaluating it, so a malformed rate card is rejected when written
     * rather than when a trade is priced.
     */
    public void validate(String expression) {
        if (isBlank(expression)) {
            return;
        }
        parse(expression);
    }

    /**
     * The variable names an expression references.
     *
     * <p>Rate cards are data, so nothing checks them at compile time. A typo such as
     * {@code #equityOrientd} parses cleanly, evaluates to null, and silently disables its rule
     * forever — the validator compares these names against the known vocabulary to catch that.
     */
    public Set<String> referencedVariables(String expression) {
        Set<String> names = new LinkedHashSet<>();
        if (isBlank(expression)) {
            return names;
        }
        Matcher matcher = VARIABLE.matcher(expression);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private <T> T read(String expression, ChargeContext context, ChargeAccumulator accumulator, Class<T> type) {
        try {
            return parse(expression).getValue(evaluationContext(context, accumulator), type);
        } catch (BadRequestException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new BadRequestException("Could not evaluate charge expression: " + expression
                    + " (" + e.getMessage() + ")");
        }
    }

    private Expression parse(String expression) {
        try {
            return parser.parseExpression(expression);
        } catch (RuntimeException e) {
            throw new BadRequestException("Invalid charge expression: " + expression
                    + " (" + e.getMessage() + ")");
        }
    }

    private StandardEvaluationContext evaluationContext(ChargeContext context, ChargeAccumulator accumulator) {
        StandardEvaluationContext evaluationContext = new StandardEvaluationContext();

        evaluationContext.setVariable("quantity", context.quantity());
        evaluationContext.setVariable("price", context.price());
        evaluationContext.setVariable("lotSize", context.lotSize());
        evaluationContext.setVariable("side", context.event() == null ? null : context.event().name());

        for (AmountBasis basis : AmountBasis.values()) {
            evaluationContext.setVariable(basis.name().toLowerCase(), context.amount(basis));
        }

        // Charges already applied, so a formula can build on them.
        ChargeLookup charges = new ChargeLookup();
        for (var line : accumulator.lines()) {
            charges.merge(line.getCode(), line.getAmount(), Double::sum);
        }
        evaluationContext.setVariable("charges", charges);

        // Instrument and user facts. Set last so a rate card can shadow nothing it should not.
        if (context.attributes() != null) {
            context.attributes().forEach(evaluationContext::setVariable);
        }
        return evaluationContext;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Backs {@code #charges['CODE']}.
     *
     * <p>A Map, because that is what SpEL's indexer syntax understands. Absent keys read as zero
     * rather than null: a formula referencing a charge that this event did not attract should treat
     * it as nothing, not fail arithmetic on a null.
     */
    private static final class ChargeLookup extends HashMap<String, Double> {

        @Override
        public Double get(Object key) {
            return getOrDefault(key, 0.0);
        }
    }
}
