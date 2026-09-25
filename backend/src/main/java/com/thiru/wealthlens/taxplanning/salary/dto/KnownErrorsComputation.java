package com.thiru.wealthlens.taxplanning.salary.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.thiru.wealthlens.taxplanning.salary.entity.TaxComputationEntity;

/**
 * The old calculator's result with the notice that it is known to be wrong (OQ-3, D-063, D-064).
 *
 * <p>The computation is unwrapped, so the response keeps the shape clients already read and gains one
 * top-level field, {@code knownErrors}. This goes when the old endpoint does — after Slice 9, once the
 * replacement passes its golden tests ({@code 09-build-plan.md} B2).
 */
public record KnownErrorsComputation(@JsonUnwrapped TaxComputationEntity computation, String knownErrors) {

    /** From {@code 00-BRIEF.md} §12 OQ-3; it types no figure, so nothing in it can drift. */
    public static final String NOTICE = "Known errors: this calculator is being replaced and its results are"
            + " known to be wrong in some cases — for example, it charges no surcharge at high incomes under"
            + " the new regime. Do not rely on these figures.";

    /** The computation, carrying the notice. */
    public static KnownErrorsComputation of(TaxComputationEntity computation) {
        return new KnownErrorsComputation(computation, NOTICE);
    }
}
