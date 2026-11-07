package com.thiru.wealthlens.taxengine.input;

/**
 * A fund whose employer contributions count towards the aggregate that is salary beyond its limit (S2 s.17(1)(h);
 * 1961 s.17(2)(vii); D-126). Any other fund is not in the aggregate.
 */
public enum EmployerFund {

    /** A recognised provident fund. */
    RECOGNISED_PROVIDENT_FUND,

    /** The notified pension scheme — S2 s.124(1); 1961 s.80CCD(1). */
    NOTIFIED_PENSION_SCHEME,

    /** An approved superannuation fund. */
    APPROVED_SUPERANNUATION_FUND
}
