package com.thiru.wealthlens.taxengine.rules.pack;

/**
 * The four parts of a rule pack, separated by change rate and authority.
 *
 * <p>One artefact, one version and one reviewer would be wrong for four things that change for
 * different reasons, on different dates, reviewed by different people. A Budget changes {@code law}.
 * A form notification changes {@code forms} — on a <b>different date</b> from the Finance Act, and
 * again mid-year: G.S.R. 822(E) substituted Forms 169 and 171 in September. A content editor fixes
 * a typo in {@code content} any afternoon.
 *
 * <p>Two consequences follow from the split, and both are the point of it:
 *
 * <ul>
 *   <li><b>Caching.</b> A computation is keyed on {@code law} and {@code exec} only, so seven
 *       editorial revisions to the claim catalogue invalidate nothing.
 *   <li><b>Review is proportionate.</b> A {@code law} change needs someone who has read the gazette.
 *       A {@code content} typo does not.
 * </ul>
 */
public enum RulePackLayer {

    /**
     * Rates, caps, conditions, regimes, disallowance sets, temporal versions.
     *
     * <p><b>The only layer that may change a number.</b> Authored by whoever read the gazette.
     */
    LAW(true),

    /**
     * Pipelines, node sets, temporal-axis bindings, mid-year combination rules.
     *
     * <p>May change a number, but only by selecting which law applies. Authored by engineers.
     */
    EXEC(true),

    /**
     * ITR form specifications, schedules and field mappings.
     *
     * <p>Presentation of computed values, so it changes no number. Versioned apart from {@code law}
     * because form notifications follow their own cadence.
     */
    FORMS(false),

    /** Display names, explanations, claim steps, counterparties, common mistakes. Changes no number. */
    CONTENT(false);

    private final boolean affectsComputation;

    RulePackLayer(boolean affectsComputation) {
        this.affectsComputation = affectsComputation;
    }

    /**
     * Whether a change in this layer can change a computed figure.
     *
     * <p>True for {@link #LAW} and {@link #EXEC} only. This is what the compute cache keys on, and
     * invariant I27 is its property test: with {@code law} and {@code exec} fixed, perturbing every
     * value in {@code forms} and {@code content} must leave every net payable byte-identical.
     */
    public boolean affectsComputation() {
        return affectsComputation;
    }
}
