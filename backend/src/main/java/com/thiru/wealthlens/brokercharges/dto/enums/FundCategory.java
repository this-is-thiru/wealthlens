package com.thiru.wealthlens.brokercharges.dto.enums;

/**
 * Scheme classification for a pooled investment.
 *
 * <p>Used for reporting and for rule eligibility predicates. It deliberately does <em>not</em>
 * encode tax treatment: whether a scheme is equity-oriented for securities transaction tax depends
 * on its actual equity allocation, not its marketing category, and an index fund, an ELSS and a
 * plain equity fund can all qualify. That determination is an explicit flag on the instrument
 * rather than something inferred from this enum.
 */
public enum FundCategory {

    /** Predominantly equity holdings. */
    EQUITY,

    /** Predominantly fixed-income holdings. */
    DEBT,

    /** A mix of equity and debt. */
    HYBRID,

    /** Very short duration debt, typically redeemable same-day. */
    LIQUID,

    /** Tax-saving equity scheme carrying a statutory lock-in. */
    ELSS,

    /** Passively tracks an index. */
    INDEX,

    /** Exchange-traded fund. */
    ETF,

    /** Invests in units of other funds. */
    FUND_OF_FUNDS,

    /** Anything not covered above. */
    OTHER
}
