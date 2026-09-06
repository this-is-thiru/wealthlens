package com.thiru.wealthlens.brokercharges.engine;

import static com.thiru.wealthlens.brokercharges.engine.ChargeAmounts.amount;
import static com.thiru.wealthlens.brokercharges.engine.ChargeAmounts.requireNonNegative;

import com.thiru.wealthlens.brokercharges.dto.context.ChargeContext;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeBasis;
import com.thiru.wealthlens.brokercharges.entity.ChargeRule;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * An amount per share traded.
 *
 * <p>Quantity is taken as the rule prices it. In the cash segment that is shares; when derivatives
 * cards arrive, a rule priced per lot will have to say so, because nothing in the context
 * distinguishes a quantity of lots from a quantity of units on its own.
 */
@Component
public class PerUnitChargeCalculator implements ChargeCalculator {

    @Override
    public ChargeBasis basis() {
        return ChargeBasis.PER_UNIT;
    }

    @Override
    public BigDecimal compute(ChargeRule rule, ChargeContext context, ChargeAccumulator accumulator) {
        BigDecimal perUnit = requireNonNegative(rule, "per unit amount", amount(rule.getPerUnitAmount()));
        return perUnit.multiply(BigDecimal.valueOf(context.quantity()));
    }
}
