package com.thiru.wealthlens.brokercharges.engine;

import com.thiru.wealthlens.brokercharges.dto.context.ChargeContext;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeBasis;
import com.thiru.wealthlens.brokercharges.entity.ChargeRule;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * An expression over the trade and the charges applied so far.
 *
 * <p>The escape hatch that keeps a new charge a data change. Mutual fund exit load is the motivating
 * case: the amount depends on how long the units were held, which no fixed field expresses and which
 * the old design could not represent at all.
 *
 * <p>The expression is evaluated in double precision inside SpEL, so its result may carry float
 * noise. That is harmless and deliberate — the engine rounds every line once, afterwards, and
 * rounding here as well would apply the policy twice.
 */
@Component
@RequiredArgsConstructor
public class FormulaChargeCalculator implements ChargeCalculator {

    private final ChargeFormulaEvaluator formulaEvaluator;

    @Override
    public ChargeBasis basis() {
        return ChargeBasis.FORMULA;
    }

    @Override
    public BigDecimal compute(ChargeRule rule, ChargeContext context, ChargeAccumulator accumulator) {
        return formulaEvaluator.evaluate(rule.getFormula(), context, accumulator);
    }
}
