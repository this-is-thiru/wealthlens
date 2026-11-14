package com.thiru.wealthlens.taxengine.rules.definition;

/**
 * Where a charge on a disposal stands in computing a capital gain (2025 s.72; 1961 s.48; W-162a, D-375): the two
 * amounts the Acts deduct from the consideration, or neither. Closed: a new limb is new arithmetic.
 */
public enum ChargeDeductionLimb {

    /** Expenditure incurred wholly and exclusively in connection with the transfer (2025 s.72(1)(a); 1961 s.48(i)). */
    TRANSFER_EXPENDITURE,

    /** Part of the cost of acquisition (2025 s.72(1)(b); 1961 s.48(ii)). */
    COST_OF_ACQUISITION,

    /** Not deducted, such as securities transaction tax (2025 s.72(3)(b); 1961 s.48, sixth proviso). */
    NOT_DEDUCTED;

    /** Whether the charge reduces the gain. */
    public boolean deducts() {
        return this != NOT_DEDUCTED;
    }
}
