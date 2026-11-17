package com.thiru.wealthlens.taxengine.rules.filing;

/** A total of the engine's computation a certificate's amount is compared with, where no single node holds it (W-130a, D-756 C1a). */
public enum ComparedTotal {
    /** The tax, surcharge and cess before anything already paid: Form No. 130's row 17, and what Part B should have deducted. */
    BILL
}
