package com.thiru.wealthlens.taxplanning.engine;

import java.util.Map;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

@Component
public class FormulaEvaluator {

    private final ExpressionParser parser = new SpelExpressionParser();

    public long evaluate(String formula, EvaluationContext context) {
        if (formula == null || formula.isBlank()) {
            return 0L;
        }
        return parser.parseExpression(formula).getValue(context, Long.class);
    }

    public long evaluate(String formula, Long basic, Long da, Long rentPaid, Long hraReceived) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setVariable("basic", basic != null ? basic : 0L);
        context.setVariable("da", da != null ? da : 0L);
        context.setVariable("rentPaid", rentPaid != null ? rentPaid : 0L);
        context.setVariable("hraReceived", hraReceived != null ? hraReceived : 0L);
        return evaluate(formula, context);
    }

    public long evaluate(String formula, Map<String, Object> variables) {
        if (formula == null || formula.isBlank()) {
            return 0L;
        }
        StandardEvaluationContext context = new StandardEvaluationContext();
        variables.forEach(context::setVariable);
        return parser.parseExpression(formula).getValue(context, Long.class);
    }
}
