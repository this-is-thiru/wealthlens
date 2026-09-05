package com.thiru.wealthlens.brokercharges.dto.enums;

/**
 * Why a charge computation produced the lines it did — or produced none.
 *
 * <p>Recorded on every stored computation so that an absent charge is queryable rather than merely
 * logged. This matters most when a user backfills historical transactions: a year with no rate card
 * on file would otherwise accrue nothing at all, and a warning in a log file scrolls away long
 * before anyone notices the gap.
 */
public enum ChargeResolution {

    /** A rate card applied and produced at least one line. */
    RESOLVED,

    /** A rate card applied but no rule matched the event, side or eligibility. */
    NO_MATCHING_RULES,

    /** No rate card covers this broker, instrument and segment on this transaction date. */
    NO_SCHEDULE,

    /**
     * A rate card applied, but the instrument carries no charge profile. Scheme-level charges such
     * as exit load could not be assessed even though broker charges were.
     */
    NO_INSTRUMENT_PROFILE,

    /**
     * The computation depends on facts that may still change — user history, or FIFO lots not yet
     * uploaded. Correct as of now, but a candidate for recomputation.
     */
    PROVISIONAL
}
