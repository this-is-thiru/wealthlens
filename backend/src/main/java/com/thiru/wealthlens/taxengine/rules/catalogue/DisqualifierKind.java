package com.thiru.wealthlens.taxengine.rules.catalogue;

/**
 * When a disqualifier bites (D-042). The moment decides what a person can still do about it, so the
 * explorer says it differently: before acting, at the claim, at filing, outside their control, or in a
 * later year.
 */
public enum DisqualifierKind {
    /** Before anything is done: the wrong assessee type, residence, age or regime. */
    NOT_ELIGIBLE,
    /** At the claim: a condition of the route is not met. */
    CONDITION_FAILED,
    /** At filing: a step was not done, or not done in time. */
    PROCESS_MISSED,
    /** Outside the person's control: the counterparty did not report or act. */
    THIRD_PARTY_MISSING,
    /** In a later year: a lock-in or holding condition is broken and the benefit is taken back. */
    LATER_REVERSAL
}
