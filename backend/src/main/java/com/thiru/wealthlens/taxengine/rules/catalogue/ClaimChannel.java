package com.thiru.wealthlens.taxengine.rules.catalogue;

/**
 * How a claim reaches the tax computed for a person ({@code DR 22} C2). The channel decides what a claim missing from
 * the employer's certificate means, which is why an entry states it rather than leaving it to its steps.
 */
public enum ClaimChannel {

    /** May go through the employer to lower the tax deducted from salary; the return takes it either way. */
    EMPLOYER_OPTIONAL,

    /** The employer cannot apply it; it is claimed in the return. */
    RETURN_ONLY,

    /** Claimed in the return, with a certificate that must exist and be producible. */
    RETURN_WITH_DOCUMENT,

    /** Allowed only on what the other party reports to the tax department. */
    THIRD_PARTY_REPORTED,

    /** Not a claim: the employer has to do it. */
    EMPLOYER_PROVIDED
}
