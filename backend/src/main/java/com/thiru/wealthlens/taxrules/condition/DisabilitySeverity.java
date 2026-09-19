package com.thiru.wealthlens.taxrules.condition;

/**
 * The two certified levels of disability the Act distinguishes.
 *
 * <p>{@code DED-80U-001} (s.154(1), (2)) allows &#8377;75,000 to a resident individual certified as
 * a person with disability, and &#8377;1,25,000 for severe disability; {@code DED-80DD-001}
 * (s.127) does the same for a dependant, where severe is defined as <b>eighty per cent or
 * more</b>. Both VERIFIED.
 *
 * <p>Ordered least to most severe, so that {@code IsCertifiedDisabled(DISABILITY)} holds for someone
 * certified severe — a minimum, not an equality.
 */
public enum DisabilitySeverity {

    /** Certified as a person with disability. */
    DISABILITY,

    /** Certified as a person with severe disability — eighty per cent or more. */
    SEVERE_DISABILITY
}
