package com.thiru.wealthlens.taxrules.regime;

/**
 * Tier 3 — cross-cutting behaviour a regime needs that is neither a node nor a value.
 *
 * <p>The escalation this sits at the top of is deliberately steep. A regime that differs in rates,
 * disallowances, eligibility, election mechanics or step order is <b>tier 1</b>: a JSON entry, no
 * code — which covers all five regimes in the 2025 Act. One needing arithmetic that does not exist
 * is <b>tier 2</b>: one new node, registered by {@code NodeId}, with no existing node changed. Only
 * a novel election protocol, a bespoke eligibility evaluation or a special interaction with set-off
 * reaches here.
 *
 * <p><b>Ship with zero implementations other than {@link DataDrivenRegimeBehaviour}.</b> If a real
 * one seems necessary, that is the signal to ask whether the {@code Condition} algebra is missing a
 * predicate instead — a predicate serves every future regime, and a behaviour serves one.
 *
 * <h2>This interface is incomplete, on purpose</h2>
 *
 * <p>{@code 17-regime-lifecycle.md} L5 gives three override hooks —
 * {@code evaluateEligibility}, {@code evaluateElection} and {@code adjustPipeline}. Each takes types
 * that do not exist yet ({@code Taxpayer}, {@code Inputs}, {@code EligibilityVerdict},
 * {@code ElectionVerdict}, {@code RegimeElectionHistory}), so they arrive with the items that build
 * them. An interface whose methods name undefined types cannot be implemented, and a default body
 * that cannot compile is worse than an absent method.
 */
public interface RegimeBehaviour {

    /** The id a pack binds this behaviour by. */
    BehaviourId id();
}
