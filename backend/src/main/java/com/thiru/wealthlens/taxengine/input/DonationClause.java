package com.thiru.wealthlens.taxengine.input;

/**
 * Which part of s.133(1) a donee falls under — what decides whether a donation counts in full or at half, and whether
 * within the qualifying limit (S2 s.133(1), (2); D-142). s.133(1)(a)(xxiv) is a company's and has no clause here. For TY
 * 2025-26 each names its 1961 s.80G(2) counterpart, which counts the same way (D-148).
 */
public enum DonationClause {

    /** A fund s.133(1)(a)(i)–(xxii) names; 1961 s.80G(2)(a)'s funds on (1)(i)'s list — the whole, without limit. */
    NAMED_FUND,

    /** For family planning — s.133(1)(a)(xxiii); 1961 s.80G(2)(a)(vii) — the whole, within the limit. */
    FAMILY_PLANNING,

    /** The Prime Minister's Drought Relief Fund — s.133(1)(b)(i); 1961 s.80G(2)(a)(iii) — half, without limit. */
    DROUGHT_RELIEF_FUND,

    /**
     * A charitable fund or institution, registered or approved — s.133(1)(b)(ii); 1961 s.80G(2)(a)(iv) — half, within the
     * limit, allowed only as it reports (s.133(6); s.80G(5D) Explanation 2A).
     */
    CHARITABLE_INSTITUTION,

    /** The bodies s.133(1)(b)(iii)–(vi) name; 1961 s.80G(2)(a)(v), (vi), (via) and (b) — half, within the limit. */
    PUBLIC_BODY;

    /** Whether the clause allows the whole of the sum (s.133(1)(a)) rather than half of it (s.133(1)(b)). */
    public boolean isWhole() {
        return this == NAMED_FUND || this == FAMILY_PLANNING;
    }
}
