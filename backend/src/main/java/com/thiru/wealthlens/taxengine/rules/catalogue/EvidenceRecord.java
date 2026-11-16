package com.thiru.wealthlens.taxengine.rules.catalogue;

/**
 * What the product keeps that answers whether a claim's document is held (PRD F25; W-120 C2a, D-718): named in the
 * catalogue beside the document, so a claim added later is checked by data, never by a list of rule ids. The engine only
 * names these; the product reads each from its own store.
 */
public enum EvidenceRecord {
    /** The months of rent receipted, per tenancy. */
    RENT_RECEIPTS,
    /** The landlords' name, address and PAN, asked above the r.205(2) threshold. */
    LANDLORD_PARTICULARS,
    /** The leave-travel expenditure evidenced, per journey. */
    JOURNEY_EXPENDITURE,
    /** The amount evidenced per Schedule XV payment code. */
    QUALIFYING_PAYMENT,
    /** The amount evidenced per health payment. */
    HEALTH_PAYMENT,
    /** The amount evidenced per donation. */
    DONATION,
    /** The amount evidenced per dependant with disability. */
    DEPENDANT_SUPPORT,
    /** Whether the lender's certificate of interest is held, an engine fact. */
    LENDER_CERTIFICATE,
    /** The lenders' name, address and PAN (r.205(2) Sl. 3). */
    LENDER_PARTICULARS,
    /** Nothing: the product keeps no record of this document, so the person checks it. */
    NOT_RECORDED
}
