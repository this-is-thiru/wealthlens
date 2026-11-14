package com.thiru.wealthlens.portfolio.dto.enums;

/**
 * How {@code portfolio} recorded that a lot came to be held: bought, or created by a corporate action, or not recorded
 * at all (rows written before W-088f). A fact, not a tax outcome: which modes keep a special rate is the tax side's
 * rule data (W-088f2), so a new way of acquiring is a new value here and new data there (D-249).
 */
public enum AcquisitionMode {
    PURCHASE,
    CORPORATE_ACTION,
    UNRECORDED
}
