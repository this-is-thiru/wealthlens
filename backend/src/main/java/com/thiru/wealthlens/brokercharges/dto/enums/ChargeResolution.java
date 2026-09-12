package com.thiru.wealthlens.brokercharges.dto.enums;

import java.util.List;

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
     *
     * <p><b>Nothing sets this today.</b> It was written by the batch upload path, which was removed
     * with the backfill: ADR-32 means transactions are never re-driven, so no caller prices a batch
     * that could reach back before what is already recorded. Kept because it is the right name for
     * that state if batching returns, and because it costs nothing to leave in the enum.
     */
    PROVISIONAL,

    /**
     * The transaction arose from a corporate action and no rule opted in to charging it. Bonus
     * shares, split allotments and demerger entitlements are issued free and must attract no
     * brokerage, tax or duty.
     *
     * <p>Recorded explicitly rather than left as an empty result, so that a zero charge on a
     * corporate action is visibly deliberate rather than indistinguishable from a missing rate card.
     */
    CORPORATE_ACTION_EXEMPT;

    /**
     * The resolutions under which the engine did not reach an answer it stands behind.
     *
     * <p>{@code NO_MATCHING_RULES} belongs here because a card can resolve and still price nothing.
     * An unscoped card — a maintenance card is the obvious one, since an AMC cycle carries no asset
     * type to scope against — matches every dimension of a trade whose asset type has no card of its
     * own. It wins by default, none of its rules declare that trade's event, and the result is a
     * zero that looks deliberate. That is the failure this design exists to prevent, reached by a
     * different route than a missing card.
     *
     * <p>{@code CORPORATE_ACTION_EXEMPT} is deliberately absent: a bonus allotment charging nothing
     * is an answer, not a gap.
     */
    public static final List<ChargeResolution> UNRESOLVED =
            List.of(NO_SCHEDULE, NO_INSTRUMENT_PROFILE, NO_MATCHING_RULES, PROVISIONAL);

    /** Whether the engine reached a figure it stands behind. */
    public boolean isUnresolved() {
        return UNRESOLVED.contains(this);
    }
}
