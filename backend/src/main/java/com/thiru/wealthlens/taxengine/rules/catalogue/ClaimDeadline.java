package com.thiru.wealthlens.taxengine.rules.catalogue;

/**
 * When a claim step has to be done by (I26; W-117a C1a). A name, never a date: the dated ones are days the tax calendar
 * works out for a year, and an employer's cut-off is the employer's own, so it is only named.
 */
public enum ClaimDeadline {

    /** The last day of the tax year. */
    TAX_YEAR_END,

    /** The day the return is due. */
    RETURN_DUE,

    /** The day the employer stops taking proofs, which each employer sets. */
    EMPLOYER_PROOF_CUT_OFF,

    /** No day to do it by. */
    NONE
}
