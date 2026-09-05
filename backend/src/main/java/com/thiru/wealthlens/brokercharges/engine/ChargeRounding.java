package com.thiru.wealthlens.brokercharges.engine;

import com.thiru.wealthlens.brokercharges.dto.enums.RoundingPolicy;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Applies a rule's rounding policy to a computed amount.
 *
 * <p>Called once, by the engine, after every other modifier — never from inside a calculator.
 * Rounding an intermediate value and rounding again compounds the error, which surfaces as paise of
 * drift against a real contract note.
 *
 * <p>Brokers do not round uniformly. Securities transaction tax and stamp duty are conventionally
 * taken to the nearest rupee, while brokerage and exchange charges carry paise, so the policy is
 * declared per rule rather than fixed here.
 */
public final class ChargeRounding {

    private static final int PAISE_SCALE = 2;
    private static final int RUPEE_SCALE = 0;

    private ChargeRounding() {
    }

    /**
     * @param amount the computed amount; must not be null
     * @param policy the rule's policy. Null is treated as {@link RoundingPolicy#HALF_UP_2}, so a
     *               rule that omits one cannot leak raw precision onto a contract note
     */
    public static BigDecimal apply(BigDecimal amount, RoundingPolicy policy) {
        if (amount == null) {
            throw new IllegalArgumentException("Charge amount to round must not be null");
        }

        RoundingPolicy effective = policy == null ? RoundingPolicy.HALF_UP_2 : policy;
        BigDecimal rounded = switch (effective) {
            case NONE -> amount;
            case HALF_UP_2 -> amount.setScale(PAISE_SCALE, RoundingMode.HALF_UP);
            case CEILING_2 -> amount.setScale(PAISE_SCALE, RoundingMode.CEILING);
            case HALF_UP_0 -> amount.setScale(RUPEE_SCALE, RoundingMode.HALF_UP);
        };

        return normaliseNegativeZero(rounded);
    }

    /**
     * A value that rounds to zero from below keeps its sign in BigDecimal, and renders as
     * {@code -0.00} — alarming on a contract note and in a JSON payload alike.
     */
    private static BigDecimal normaliseNegativeZero(BigDecimal value) {
        return value.signum() == 0 ? value.abs() : value;
    }
}
