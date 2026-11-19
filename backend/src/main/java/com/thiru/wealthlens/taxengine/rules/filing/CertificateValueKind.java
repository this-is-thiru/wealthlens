package com.thiru.wealthlens.taxengine.rules.filing;

/** What a certificate's line holds, as the form prints it (W-126a). */
public enum CertificateValueKind {
    /** A rupee amount, in one cell or one per column. */
    AMOUNT,
    /** Yes or No. */
    YES_NO,
    /** Words, numbers that are not amounts, or dates as printed. */
    TEXT,
    /** Two dates as the form prints them, "dd/mm/yyyy to dd/mm/yyyy", such as Form No. 130's period with the employer (W-128b). */
    PERIOD,
    /** A heading line that groups the lines under it and holds no value of its own. */
    NONE
}
