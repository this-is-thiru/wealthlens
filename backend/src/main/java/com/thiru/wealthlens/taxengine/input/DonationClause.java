package com.thiru.wealthlens.taxengine.input;

/**
 * Which part of s.133(1) a donee falls under — what decides whether a donation counts in full or at half, and whether
 * within the qualifying limit (S2 s.133(1), (2); D-142). s.133(1)(a)(xxiv) is a company's and has no clause here.
 */
public enum DonationClause {

    /** A fund s.133(1)(a)(i)–(xxii) names — the whole, without limit. */
    NAMED_FUND,

    /** The Government, a local authority, institution or association, for family planning — s.133(1)(a)(xxiii). */
    FAMILY_PLANNING,

    /** The Prime Minister's Drought Relief Fund — s.133(1)(b)(i), half, without limit. */
    DROUGHT_RELIEF_FUND,

    /** A charitable fund or institution, registered or approved — s.133(1)(b)(ii); allowed only as it reports (6). */
    CHARITABLE_INSTITUTION,

    /** The Government, a local authority or another body s.133(1)(b)(iii)–(vi) names — half, within the limit. */
    PUBLIC_BODY;

    /** Whether the clause allows the whole of the sum (s.133(1)(a)) rather than half of it (s.133(1)(b)). */
    public boolean isWhole() {
        return this == NAMED_FUND || this == FAMILY_PLANNING;
    }
}
