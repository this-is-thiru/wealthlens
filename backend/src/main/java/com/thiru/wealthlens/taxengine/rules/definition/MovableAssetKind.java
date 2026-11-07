package com.thiru.wealthlens.taxengine.rules.definition;

/**
 * A movable asset an employer lets the employee use, by the classes the Rules exclude from the 10% valuation (S4
 * r.15(5) Table IV Sl. 7; Rules 1962 r.3(7)(vii); D-119). In the rules vocabulary because {@link ExcludedAssets}
 * lists them by year.
 */
public enum MovableAssetKind {

    /** A laptop or computer — excluded in both years. */
    LAPTOP_OR_COMPUTER,

    /** A tablet — excluded from TY 2026-27. */
    TABLET,

    /** A mobile phone — excluded from TY 2026-27. */
    MOBILE_PHONE,

    /** Any other movable asset. */
    OTHER
}
