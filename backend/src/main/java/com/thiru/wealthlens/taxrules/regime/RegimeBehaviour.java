package com.thiru.wealthlens.taxrules.regime;

/**
 * Tier 3 of {@code 17-regime-lifecycle.md} L5: behaviour a regime needs that is neither a number
 * nor a node.
 *
 * <p>Optional per regime, and <b>the vast majority of regimes never declare one</b>. L5's escalation
 * is deliberate: a regime differing in rates, disallowances, eligibility, election mechanics or step
 * order is a JSON entry (tier 1, which covers all five regimes in the 2025 Act); one needing new
 * arithmetic is a node (tier 2); only genuinely novel cross-cutting behaviour reaches here.
 *
 * <p>L5's own recommendation is to ship with <b>zero</b> implementations other than
 * {@link DataDrivenRegimeBehaviour}. If a real one seems necessary, that is the signal to ask
 * whether the {@code Condition} algebra is missing a predicate instead — a predicate serves every
 * future regime, a behaviour serves one.
 *
 * <p>The interface carries only what can compile. L5 declares two further hooks,
 * {@code evaluateEligibility} and {@code evaluateElection}, which name five types that do not exist
 * yet; each arrives with the item that builds its types ({@code W-031b}'s erratum).
 */
public interface RegimeBehaviour {

    BehaviourId id();

    /**
     * Last-resort hook to adjust the assembled pipeline. <b>Use is a review trigger.</b>
     *
     * <p>Overriding this is allowed; doing so invisibly is not. Guard 3 of L5's table is an
     * {@code ArchitectureTest} case that <b>lists</b> every class overriding it and compares the
     * list against a reviewed one, so a new override fails the build until somebody has looked at
     * it. A regime that rewrites the pipeline is the single easiest way to make the computation
     * stop matching the rulebook, and it would otherwise be invisible in review.
     *
     * <p><b>No inputs parameter, deliberately.</b> L5 writes this as
     * {@code adjustPipeline(PipelineSpec declared, Inputs in)}. {@code Inputs} is A3's
     * {@code EngineInputs}, which is a {@code taxengine} type — and this interface is in
     * {@code taxrules}, which {@code taxengine} already depends on. Taking it here would need
     * {@code taxrules -> taxengine}, a <b>cycle</b>, which Spring Modulith rejects outright
     * (blocker {@code B-001} established this; decision <b>D-027</b> records it for this hook). The
     * parameter is speculative until a real tier-3 behaviour exists to say what it needs, and
     * adding it then is a one-line change to an interface with one implementation.
     *
     * @param declared the pipeline as assembled from the pack
     * @return the pipeline to run; the default returns it unchanged
     */
    default PipelineSpec adjustPipeline(PipelineSpec declared) {
        return declared;
    }
}
