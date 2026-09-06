package com.thiru.wealthlens.brokercharges.dto.enums;

/**
 * What kind of party ultimately receives a charge.
 *
 * <p>Drives grouping on contract notes and reports. It carries no calculation semantics — whether
 * a charge is taxable is declared per rule, not inferred from its category, because the taxable
 * set differs by instrument and changes with regulation.
 */
public enum ChargeCategory {

    /** The broker's own fee for executing the order. */
    BROKERAGE,

    /** Levied by government and never itself taxed: STT, stamp duty. */
    STATUTORY,

    /** Payable to the exchange: transaction charges, IPFT, clearing. */
    EXCHANGE,

    /** Payable to the regulator: SEBI turnover fees. */
    REGULATORY,

    /** Payable to the depository: DP charges on debit of securities. */
    DEPOSITORY,

    /** Tax computed over other charges: GST. */
    TAX,

    /** Recurring or one-off account charges: AMC, account opening, platform fees. */
    SUBSCRIPTION,

    /** Charged by the fund rather than the broker: exit load, transaction fee. */
    FUND
}
