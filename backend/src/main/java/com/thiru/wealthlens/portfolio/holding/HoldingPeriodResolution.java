package com.thiru.wealthlens.portfolio.holding;

import com.thiru.wealthlens.portfolio.dto.enums.CapitalGainsType;

/**
 * The classification, and the policy that decided it.
 *
 * <p>The policy code is carried so a figure on a tax statement can be traced back to the rule that
 * produced it. A classification with no provenance cannot be argued with, and this one will
 * eventually have to be.
 *
 * @param policyCode null only when nothing matched, which is what {@code UNCLASSIFIED} means
 */
public record HoldingPeriodResolution(CapitalGainsType capitalGainsType, String policyCode, String reason) {

    static HoldingPeriodResolution unclassified(String reason) {
        return new HoldingPeriodResolution(CapitalGainsType.UNCLASSIFIED, null, reason);
    }
}
