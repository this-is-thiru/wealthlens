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
    QUALIFYING_PAYMENT
}
