package com.thiru.wealthlens.brokercharges.engine;

import com.thiru.wealthlens.brokercharges.entity.ChargeRule;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import java.math.BigDecimal;

/**
 * Shared arithmetic guards for the calculators.
 *
 * <p>Every calculator faces the same two questions — what an absent rule value means, and what a
 * negative one means — and they have to answer them identically. An absent value is nothing; a
 * negative one is a rate-card error, because a charge that pays the user is not a discount, it is a
 * defect that no reconciliation would explain.
 */
final class ChargeAmounts {

    private ChargeAmounts() {
    }

    /** A rule value as an amount. Absent means nothing, never a default that invents a charge. */
    static BigDecimal amount(Double value) {
        return value == null ? BigDecimal.ZERO : BigDecimal.valueOf(value);
    }

    static BigDecimal requireNonNegative(ChargeRule rule, String what, BigDecimal value) {
        if (value.signum() < 0) {
            throw new BadRequestException("Charge rule " + rule.getCode() + " has a negative " + what
                    + " (" + value.toPlainString() + "). A charge cannot pay the user.");
        }
        return value;
    }
}
