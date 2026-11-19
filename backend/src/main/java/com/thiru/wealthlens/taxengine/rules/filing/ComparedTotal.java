package com.thiru.wealthlens.taxengine.rules.filing;

/** A total of the engine's computation a certificate's amount is compared with, where no single node holds it (W-130a, D-756 C1a). */
public enum ComparedTotal {
    /** The tax, surcharge and cess before anything already paid: Form No. 130's row 17, and what Part B should have deducted. */
    BILL,
    /** The Chapter VIII deductions allowed in all: Form No. 130's row 11, Deductible Amount (W-130b). */
    CHAPTER_VIII,
    /** The house-property and other-sources heads before set-off: Form No. 130's row 8, other income reported (W-130d). */
    OTHER_HEADS
}
