package com.thiru.wealthlens.taxplanning.compute;

/**
 * A kind of document a person can hold for a claim (W-119b), each named as its closing sentence is coded in the year's
 * content (W-119c C4a).
 */
enum EvidenceKind {

    /** A month's rent receipt, per tenancy. */
    RENT_RECEIPTS,

    /** What a leave-travel journey cost, per journey. */
    JOURNEY_EXPENDITURE,

    /** A Schedule XV payment, per code. */
    QUALIFYING_PAYMENT,
    /** A health payment's certificate or receipt, per payment (W-119b1). */
    HEALTH_PAYMENT,
    /** A donation's receipt, per donation. */
    DONATION,
    /** The lender's certificate of education-loan interest, per loan. */
    EDUCATION_LOAN_INTEREST,
    /** The lender's certificate of house- or vehicle-loan interest, per loan. */
    PURPOSE_LOAN_INTEREST,
    /** Bills or scheme receipts for a dependant with disability, per dependant. */
    DEPENDANT_SUPPORT,
    /** Bills for a specified disease's treatment, per treatment. */
    SPECIFIED_DISEASE_TREATMENT,
    /** The Agniveer fund's statement of the person's own subscription. */
    AGNIVEER_SUBSCRIPTION
}
