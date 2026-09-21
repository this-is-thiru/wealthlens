package com.thiru.wealthlens.taxrules.regime;

/**
 * Where a taxpayer stands with respect to one regime.
 *
 * <p>{@code 17-regime-lifecycle.md:213}, unchanged. The five states are what
 * {@code RegimeElectionHistory.stateAsAt(TaxYearId)} resolves to for a given year — a projection of
 * an append-only event log, never a stored mutable flag, because a recomputation must be able to
 * reproduce what the answer was in an earlier year.
 *
 * <p>It lives here rather than with the election history ({@code W-082}) because
 * {@code taxengine}'s {@code RegimeElectionSnapshot} is typed in it, and the engine can see
 * {@code taxrules} and not {@code taxplanning} — decision <b>D-025</b>, following <b>D-024</b>.
 * It sits beside {@code ElectionProtocol}, {@code ElectionKind}, {@code ElectionVehicle} and
 * {@code ElectionRoute}, which are the vocabulary it completes.
 */
public enum ElectionState {

    /** No election has ever been made. The default, and what an absent entry means. */
    NEVER_ELECTED,

    /** Elected, and the election still stands. */
    IN_EFFECT,

    /** Elected and later withdrawn; whether it may be re-elected is the protocol's business. */
    WITHDRAWN,

    /**
     * Withdrawn under a protocol that allows no return.
     *
     * <p>Distinct from {@link #WITHDRAWN} precisely because the difference is not recoverable from
     * the event log alone — it depends on which regime's protocol applied at the time.
     */
    PERMANENTLY_INELIGIBLE,

    /** The regime itself ended; the election lapsed with it rather than being given up. */
    LAPSED
}
