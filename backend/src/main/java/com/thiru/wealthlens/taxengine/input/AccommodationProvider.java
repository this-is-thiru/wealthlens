package com.thiru.wealthlens.taxengine.input;

/** Who provides the accommodation and on what footing — S4 r.15(2) Table I Sl. 1–3; Rules 1962 r.3(1) (D-111). */
public enum AccommodationProvider {

    /** The Central or a State Government, to its own employee — Sl. 1: the licence fee. */
    GOVERNMENT,

    /** Any other employer, owning it — Sl. 2(a): a percentage of salary by the city's population. */
    EMPLOYER_OWNED,

    /** Any other employer, taking it on lease or rent — Sl. 2(b): the rental or ten per cent of salary. */
    EMPLOYER_LEASED,

    /** A hotel — Sl. 3: the charges or twenty-four per cent of salary. */
    HOTEL
}
