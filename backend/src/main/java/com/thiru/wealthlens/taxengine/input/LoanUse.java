package com.thiru.wealthlens.taxengine.input;

/**
 * What capital borrowed for a house was used for, as S2 s.22(1)(b) lists it (1961 s.24(b); D-156): only acquiring or
 * constructing the house earns the pre-construction instalments (s.22(1)(c)) and the higher limit on an own residence
 * (s.22(2)(a)(i)).
 */
public enum LoanUse {

    /** Acquiring or constructing the house. */
    ACQUISITION_OR_CONSTRUCTION,

    /** Repairing, renewing or reconstructing it. */
    REPAIR_OR_RECONSTRUCTION
}
