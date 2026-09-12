package com.thiru.wealthlens.portfolio.holding;

import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import java.time.LocalDate;

/**
 * One rule: for this asset type and sub-class, between these dates, the answer is this.
 *
 * <p>Validity-windowed for the same reason a rate card is (ADR-12, ADR-26). Holding-period rules
 * changed in 2023 and again in 2024, and a trade must be classified by the rule in force when it
 * happened — not by today's. Editing a policy would silently reclassify history; a change ships as a
 * new row whose window begins where the old one ends.
 *
 * @param policyCode    quotable, and recorded on the resolution so a figure can be traced to a rule
 * @param subClass      null matches any sub-class; a set value only matches that one, and wins on
 *                      specificity when both are candidates
 * @param keyedOn       which date {@code effectiveFrom}/{@code effectiveTo} are measured against
 * @param months        the threshold, only when the outcome is {@code LONG_TERM_AFTER_MONTHS}
 */
public record HoldingPeriodPolicy(String policyCode, AssetType assetType, String subClass,
                                  HoldingPeriodDate keyedOn, LocalDate effectiveFrom, LocalDate effectiveTo,
                                  HoldingPeriodOutcome outcome, Integer months, String note) {

    boolean appliesTo(HoldingPeriodQuery query) {
        if (assetType != query.assetType()) {
            return false;
        }
        if (subClass != null && !subClass.equals(query.subClass())) {
            return false;
        }
        LocalDate governing = query.dateFor(keyedOn);
        if (governing == null) {
            return false;
        }
        return !governing.isBefore(effectiveFrom) && (effectiveTo == null || !governing.isAfter(effectiveTo));
    }

    /** A policy naming a sub-class is more specific than one that matches any. */
    int specificity() {
        return subClass == null ? 0 : 1;
    }
}
