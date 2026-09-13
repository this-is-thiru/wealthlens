package com.thiru.wealthlens.brokercharges.dto.enums;

/**
 * Where a computed charge line's rule came from.
 *
 * <p>Charges have two independent origins. Brokerage, statutory and exchange charges belong to the
 * broker's rate card and are identical across every instrument it trades. Exit load belongs to the
 * scheme itself: two mutual funds bought through the same broker on the same day carry different
 * exit loads, and one may carry none.
 *
 * <p>Both sources contribute rules to a single ordered evaluation, so tax bases and rounding behave
 * identically regardless of origin. This is recorded on each line for provenance, so a contract note
 * can show who levied what.
 */
public enum ChargeRuleSource {

    /** From the broker's rate card, scoped by broker, asset type and segment. */
    SCHEDULE,

    /** From the instrument itself, scoped by scheme. Exit loads and scheme-level fees. */
    INSTRUMENT
}
