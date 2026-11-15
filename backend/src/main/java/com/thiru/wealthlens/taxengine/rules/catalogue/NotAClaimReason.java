package com.thiru.wealthlens.taxengine.rules.catalogue;

/** Why a rule has no claim catalogue entry and never will (W-117d C1a, C2a). */
public enum NotAClaimReason {
    /** It never writes a deduction or an exemption: income, a rate, a measure or a step of the bill. */
    NOT_A_DEDUCTION_OR_EXEMPTION,
    /** It deducts, exempts, bars or caps with nothing for the person to do: a standard deduction, a limit or a bar. */
    APPLIES_WITHOUT_A_CLAIM
}
