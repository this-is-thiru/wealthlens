package com.thiru.wealthlens.taxengine.input;

/**
 * How the owner used a house in the tax year, as the annual value turns on it (S2 s.21(1), (6), (7); 1961 s.23(1)–(4);
 * {@code DR 01} H-12; D-155).
 *
 * <p>A house held as stock-in-trade is a business asset: its own nil annual value (S2 s.21(5); 1961 s.23(5)) comes with
 * the business head, and until then it is not entered here (D-155, D-157).
 */
public enum HouseOccupancy {

    /**
     * Occupied for the owner's own residence, or not occupied for any reason, and specified by the assessee as one of
     * the houses whose annual value is nil — never let at any time in the year.
     */
    OWN_RESIDENCE,

    /** Let, wholly or in part, at any time in the year. */
    LET,

    /** Neither let nor specified as an own residence: valued at what it might be expected to let for. */
    NOT_LET
}
