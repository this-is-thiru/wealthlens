package com.thiru.wealthlens.taxplanning.documents.ingest;

/**
 * Which form a document is, as the paper says — not which year issues it. Each needs its own parser, so a new form is a new
 * parser and a new constant; which year a parser reads is the parser's to declare (W-125; D-727 C2a).
 */
public enum DocumentType {
    /** Form No. 16, the employer's certificate of tax deducted from salary. */
    FORM_16,
    /** Form No. 130, the employer's certificate under the Income-tax Rules, 2026. */
    FORM_130,
    /** Form No. 122, the particulars of salary from a previous employer. */
    FORM_122,
    /** Form No. 123, the particulars of perquisites. */
    FORM_123
}
