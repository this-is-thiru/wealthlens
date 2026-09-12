package com.thiru.wealthlens.brokercharges.engine;

import static com.thiru.wealthlens.brokercharges.engine.ChargeAmounts.amount;
import static com.thiru.wealthlens.brokercharges.engine.ChargeAmounts.requireNonNegative;

import com.thiru.wealthlens.brokercharges.dto.context.ChargeContext;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeBasis;
import com.thiru.wealthlens.brokercharges.entity.ChargeRule;
import java.math.BigDecimal;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

/**
 * A percentage of other charges, named explicitly by code.
 *
 * <p>Naming the base is the whole design. The superseded implementation applied GST to a merged
 * government-charges bucket that included securities transaction tax and stamp duty, neither of
 * which is taxable — roughly ₹17 of overcharge on a ₹1,00,000 sell (D1). A rule that has to list
 * what it taxes cannot make that mistake silently.
 *
 * <p>Reading the accumulator is also why evaluation order is validated when a card is written: a
 * derived rule sees only the lines emitted before it.
 */
@Log4j2
@Component
public class DerivedChargeCalculator implements ChargeCalculator {

    private static final int PERCENT = 2;

    @Override
    public ChargeBasis basis() {
        return ChargeBasis.DERIVED;
    }

    @Override
    public BigDecimal compute(ChargeRule rule, ChargeContext context, ChargeAccumulator accumulator) {
        BigDecimal rate = requireNonNegative(rule, "rate", amount(rule.getRate()));

        List<String> baseCodes = rule.getBaseCodes();
        if (baseCodes == null || baseCodes.isEmpty()) {
            // Taxing everything charged so far would be a guess, and would reintroduce D1 by the
            // back door on the first card that forgot the field.
            log.warn("Derived charge rule {} names no base codes; charging nothing", rule.getCode());
            return BigDecimal.ZERO;
        }

        return rate.multiply(accumulator.sumOf(baseCodes)).movePointLeft(PERCENT);
    }
}
