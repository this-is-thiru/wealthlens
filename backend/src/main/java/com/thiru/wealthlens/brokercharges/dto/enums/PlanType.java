package com.thiru.wealthlens.brokercharges.dto.enums;

/**
 * Whether a scheme is held directly with the asset management company or through a distributor.
 *
 * <p>This is what decides whether a distributor transaction fee can be levied at all: a direct plan
 * has no distributor, so it never attracts one. The fee's <em>amount</em> is the broker's decision,
 * but its <em>applicability</em> is a property of the scheme — which is why the rule lives on the
 * broker's rate card while its eligibility predicate reads this field from the instrument.
 */
public enum PlanType {

    /** Held directly with the AMC. No distributor commission or transaction fee. */
    DIRECT,

    /** Held through a distributor, who may levy a transaction fee. */
    REGULAR
}
