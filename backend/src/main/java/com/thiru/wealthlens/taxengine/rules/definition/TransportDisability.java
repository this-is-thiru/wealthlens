package com.thiru.wealthlens.taxengine.rules.definition;

/**
 * The disabilities for which a commuting allowance is exempt, as the Rules name them (S4 r.280(2) Table Sl. 10;
 * Rules 1962 r.2BB(2) Table Sl. 11; D-102). A closed set because the Rules' list is closed; which of them a year
 * admits is the rule's, not this type's — TY 2025-26 admits the lower extremities only.
 */
public enum TransportDisability {

    /** "blind". */
    BLIND,

    /** "deaf and dumb". */
    DEAF_AND_DUMB,

    /** "orthopaedically handicapped with disability of lower … extremities". */
    ORTHOPAEDIC_LOWER_EXTREMITIES,

    /** "… or upper extremities" — Rules 2026 only. */
    ORTHOPAEDIC_UPPER_EXTREMITIES
}
