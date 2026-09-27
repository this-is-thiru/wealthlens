package com.thiru.wealthlens.taxengine.input;

/**
 * The city's population "as per 2011 census" that owned accommodation's percentage turns on (S4 r.15(2) Table I
 * Sl. 2(a); Rules 1962 r.3(1); D-111). The Rules name no cities, so the taxpayer states the band.
 */
public enum PopulationBand {

    /** "cities having population exceeding forty lakhs". */
    ABOVE_FORTY_LAKH,

    /** "cities having population exceeding fifteen lakhs but not exceeding forty lakhs". */
    FIFTEEN_TO_FORTY_LAKH,

    /** "other areas" — named as every location table's otherwise-place is (D-099), one word per concept. */
    ANY_OTHER_PLACE
}
