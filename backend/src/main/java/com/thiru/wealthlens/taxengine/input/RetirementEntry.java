package com.thiru.wealthlens.taxengine.input;

/**
 * The entry of the Acts a retirement or termination payment falls under (S2 s.19(1) Table; 1961 s.10(10)–(10C);
 * D-103).
 *
 * <p>Which entry applies turns on the employer and the scheme the payment was made under — facts the engine cannot
 * infer from an amount, and which the employer states on Form 16 — so the taxpayer states it. Only the entries whose
 * amount the Acts fully state are here; those resting on a notification or on another Act arrive with their items.
 */
public enum RetirementEntry {

    /** Death-cum-retirement gratuity under the Central Government's pension rules or a similar scheme — Sl. 3; 1961
     * s.10(10)(i). */
    GOVERNMENT_GRATUITY,

    /** Retiring gratuity under the defence services' Pension Code or Regulations — Sl. 4; 1961 s.10(10)(i). */
    DEFENCE_RETIRING_GRATUITY,

    /** Commuted pension under the Civil Pensions (Commutation) Rules or a similar scheme — Sl. 7; 1961
     * s.10(10A)(i). */
    GOVERNMENT_COMMUTATION,

    /** Commuted pension under a scheme of any other employer — Sl. 8; 1961 s.10(10A)(ii). */
    OTHER_EMPLOYER_COMMUTATION,

    /** Commuted pension from a pension fund — Sl. 9 (Sch. VII Sl. 3); 1961 s.10(10A)(iii) (s.10(23AAB)). */
    FUND_COMMUTATION,

    /** A workman's retrenchment compensation under a scheme the Central Government approved — Sl. 11; 1961 s.10(10B)
     * second proviso. */
    APPROVED_SCHEME_RETRENCHMENT,

    /** An amount on voluntary retirement or termination under a scheme — Sl. 12; 1961 s.10(10C). */
    VOLUNTARY_RETIREMENT,

    /** A Central or State Government employee's leave salary for earned leave at retirement — Sl. 13; 1961
     * s.10(10AA)(i). */
    GOVERNMENT_LEAVE_ENCASHMENT,

    /** Any other gratuity, on retirement, incapacity or termination — Sl. 6; 1961 s.10(10)(iii) (D-106). */
    OTHER_GRATUITY,

    /** Leave salary at retirement for an employee who is not a Central or State Government employee — Sl. 14; 1961
     * s.10(10AA)(ii) (D-106). */
    OTHER_LEAVE_ENCASHMENT
}
