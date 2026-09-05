package com.thiru.wealthlens.brokercharges.dto.enums;

/**
 * How a computed charge line is rounded.
 *
 * <p>Applied once, by the engine, after every other modifier — never inside a calculator. Rounding
 * an intermediate value and then rounding again compounds the error, which shows up as paise of
 * drift against a real contract note.
 *
 * <p>Brokers do not round uniformly: statutory charges such as STT and stamp duty are conventionally
 * rounded to the nearest rupee, while brokerage and exchange charges carry two decimals.
 */
public enum RoundingPolicy {

    /** No rounding; the computed value is stored as-is. */
    NONE,

    /** Nearest paisa, halves away from zero. The default for most charges. */
    HALF_UP_2,

    /** Always up to the next paisa. */
    CEILING_2,

    /** Nearest rupee, halves away from zero. Used for STT and stamp duty. */
    HALF_UP_0
}
