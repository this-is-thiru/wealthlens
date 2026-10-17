package com.thiru.wealthlens.taxengine.input;

/**
 * What was received without, or for inadequate, consideration, as the threshold and the amount charged turn on it
 * (S2 s.92(2)(m)(i)–(iii); 1961 s.56(2)(x)(a)–(c); D-201).
 */
public enum GiftKind {

    /** A sum of money, which is reached only when received without consideration. */
    SUM_OF_MONEY,

    /** Immovable property, being land or building or both, valued at its stamp duty value. */
    IMMOVABLE_PROPERTY,

    /**
     * Other property — shares and securities, jewellery, archaeological collections, drawings, paintings, sculptures,
     * any work of art, bullion or a virtual digital asset (S2 s.92(5)(f); 1961 s.56(2)(vii) Explanation (d), with
     * FA 2022's virtual digital asset) — valued at its fair market value.
     */
    OTHER_PROPERTY
}
