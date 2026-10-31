package com.thiru.wealthlens.taxengine.input;

/**
 * Why a sum or property received is outside the charge on gifts altogether (S2 s.92(3); 1961 s.56(2)(x) proviso;
 * D-201). The ground is the person's own claim, as the return's schedule asks for its reason; whether its conditions
 * are met is not judged here.
 */
public enum GiftExclusion {

    /** From a relative (S2 s.92(5)(g); 1961 s.56(2)(vii) Explanation (e)). */
    RELATIVE,

    /** On the occasion of the individual's marriage. */
    MARRIAGE,

    /** Under a will or by way of inheritance. */
    WILL_OR_INHERITANCE,

    /** In contemplation of the death of the payer or donor. */
    CONTEMPLATION_OF_DEATH,

    /** From a local authority. */
    LOCAL_AUTHORITY,

    /**
     * From or by a registered non-profit organisation, not received by a person it may not benefit (S2 s.92(3)(f),
     * s.355; 1961 proviso (VI)–(VIII), with FA 2022's s.13(3) carve-out).
     */
    NON_PROFIT,

    /** By way of a transaction not regarded as a transfer (S2 s.70(1), the listed clauses; 1961 s.47). */
    NOT_A_TRANSFER,

    /** From an individual by a trust created solely for the benefit of the individual's relatives. */
    RELATIVES_TRUST,

    /** From a prescribed class of persons, on its conditions (S4 r.58; 1961 proviso (XI)). */
    PRESCRIBED_CLASS,

    /**
     * For an individual's own or family member's medical treatment for an illness related to COVID-19 — TY 2025-26
     * only (1961 proviso (XII), FA 2022 s.16); the 2025 Act has no such ground.
     */
    COVID_TREATMENT,

    /**
     * To a family member of a person whose death was related to COVID-19, from the deceased's employer — TY 2025-26
     * only (1961 proviso (XIII)(A)).
     */
    COVID_DEATH_FROM_EMPLOYER,

    /**
     * To a family member of a person whose death was related to COVID-19, from anyone else — TY 2025-26 only, and
     * only within the year's limit (1961 proviso (XIII)(B)).
     */
    COVID_DEATH_FROM_OTHERS
}
