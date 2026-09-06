package com.thiru.wealthlens.brokercharges.engine;

import static com.thiru.wealthlens.brokercharges.engine.ChargeAmounts.amount;
import static com.thiru.wealthlens.brokercharges.engine.ChargeAmounts.requireNonNegative;

import com.thiru.wealthlens.brokercharges.dto.context.ChargeContext;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeBasis;
import com.thiru.wealthlens.brokercharges.entity.ChargeRule;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * A fixed amount per chargeable event, whatever the trade's size.
 *
 * <p>A zero here is a real rate rather than a missing one — a discount broker's delivery brokerage
 * genuinely is nothing, and the rule says so explicitly.
 */
@Component
public class FlatChargeCalculator implements ChargeCalculator {

    @Override
    public ChargeBasis basis() {
        return ChargeBasis.FLAT;
    }

    @Override
    public BigDecimal compute(ChargeRule rule, ChargeContext context, ChargeAccumulator accumulator) {
        return requireNonNegative(rule, "flat amount", amount(rule.getFlatAmount()));
    }
}
