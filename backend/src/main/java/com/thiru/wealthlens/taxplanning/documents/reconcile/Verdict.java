package com.thiru.wealthlens.taxplanning.documents.reconcile;

/** M4.2's verdicts, decided as D-755 C2a adopted. */
public enum Verdict {
    /** Every line within ₹10. */
    MATCHED,
    /** Every difference has an expected reason: the user's own choices, the form's own split, rounding. */
    MINOR_DIFFERENCE,
    /** A difference an employer's error explains, or nothing does. */
    MATERIAL_DIFFERENCE,
    /** The certificates cannot be read as one year: some employers issued Part C and some did not (W-129's MIXED). */
    CANNOT_COMPARE
}
