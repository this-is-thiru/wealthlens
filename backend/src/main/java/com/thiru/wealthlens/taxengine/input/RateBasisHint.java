package com.thiru.wealthlens.taxengine.input;

/**
 * Which rate basis a capital-gains bucket falls on.
 *
 * <p>{@code 13-edge-cases.md} R-10 and {@code 15-review-findings.md} F5. The boundary with
 * {@code portfolio} is drawn at <b>classification and quantification</b>, not at taxation:
 * {@code portfolio} says which disposals happened, their FIFO cost basis and the amount of gain;
 * the tax side applies ss.196/197/198 and the surcharge split. So the read-model carries a
 * <b>hint about the basis</b> and never a tax amount — {@code RATE-SUR-SPLIT-001} needs "the
 * income-tax computed in respect of that part of income", and only the side holding a rule pack can
 * compute it.
 *
 * <p>It lives beside {@link CapitalGainsInput}, its one user, in the package other modules may see: the tax side
 * decides the basis when it maps {@code portfolio}'s read-model, which carries none (D-237, D-241). One copy only — a
 * second would be the two-vocabularies failure {@code 02-lessons.md} L1 describes (D-025).
 *
 * <p><b>An unrecognised basis is a blocking error, not a fallback to slab</b> (R-10, and
 * {@code 13-edge-cases.md} row 67). Defaulting an unknown basis to the slab ladder produces a
 * plausible number that is wrong, which is the failure this whole boundary exists to prevent.
 */
public enum RateBasisHint {

    /** Listed equity and equity-oriented funds, which carry their own rates. */
    SPECIAL_RATE_LISTED_EQUITY,

    /** Any other asset charged at a special rate rather than through the ladder. */
    SPECIAL_RATE_OTHER,

    /** Charged through the slab ladder with the rest of total income. */
    SLAB
}
