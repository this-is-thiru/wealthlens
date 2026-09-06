package com.thiru.wealthlens.brokercharges.engine;

import static com.thiru.wealthlens.brokercharges.engine.ChargeAmounts.amount;
import static com.thiru.wealthlens.brokercharges.engine.ChargeAmounts.requireNonNegative;

import com.thiru.wealthlens.brokercharges.dto.context.ChargeContext;
import com.thiru.wealthlens.brokercharges.dto.enums.AmountBasis;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeBasis;
import com.thiru.wealthlens.brokercharges.entity.ChargeRule;
import java.math.BigDecimal;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

/**
 * A percentage of the context amount the rule names.
 *
 * <p>Which amount is the whole point of the {@code amountBasis} field. In the cash segment it is
 * always turnover, but an option is priced on premium and a future on notional — declaring it per
 * rule is what keeps adding derivatives a rate-card change rather than a schema migration.
 */
@Log4j2
@Component
public class TurnoverChargeCalculator implements ChargeCalculator {

    private static final int PERCENT = 2;

    @Override
    public ChargeBasis basis() {
        return ChargeBasis.TURNOVER;
    }

    @Override
    public BigDecimal compute(ChargeRule rule, ChargeContext context, ChargeAccumulator accumulator) {
        BigDecimal rate = requireNonNegative(rule, "rate", amount(rule.getRate()));
        AmountBasis basis = rule.effectiveAmountBasis();
        BigDecimal base = requireNonNegative(rule, "base amount", BigDecimal.valueOf(context.amount(basis)));

        if (rate.signum() != 0 && !carries(context, basis)) {
            // Zero rather than a fallback to turnover: charging a percentage of an unrelated number
            // would be worse than charging nothing, and harder to spot.
            log.warn("Charge rule {} prices on {}, which transaction {} does not carry; charging nothing",
                    rule.getCode(), basis, context.transactionId());
        }

        return rate.multiply(base).movePointLeft(PERCENT);
    }

    private static boolean carries(ChargeContext context, AmountBasis basis) {
        return context.baseAmounts() != null && context.baseAmounts().containsKey(basis);
    }
}
