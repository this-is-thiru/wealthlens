package com.thiru.wealthlens.taxengine.rules.definition;

/**
 * A part of the capital-gains head that losses are set off against and that is taxed on its own basis (D-243): the
 * term the gain arose over and the rate it is charged at.
 *
 * <p>Four, because the special-rate provisions make four: a short-term gain on equity sold with securities
 * transaction tax (S2 s.196; 1961 s.111A) and any other short-term gain, charged with total income; a long-term gain on
 * equity bought and sold with it (S2 s.198; 1961 s.112A) and any other long-term gain (S2 s.197; 1961 s.112).
 */
public enum CapitalGainsPart {

    SHORT_TERM_SLAB,

    SHORT_TERM_LISTED_EQUITY,

    LONG_TERM_OTHER,

    LONG_TERM_LISTED_EQUITY;

    /** Whether the part is long-term: a long-term loss may be set off only against these (S2 s.108(2)(b)). */
    public boolean isLongTerm() {
        return switch (this) {
            case LONG_TERM_OTHER, LONG_TERM_LISTED_EQUITY -> true;
            case SHORT_TERM_SLAB, SHORT_TERM_LISTED_EQUITY -> false;
        };
    }
}
