package com.thiru.wealthlens.taxengine.trace;

/**
 * How much attention a {@link Notice} deserves.
 *
 * <p>The four values are {@code 05-domain-model.md} M5.1's list, unchanged. None of them is an
 * error: a notice is data hanging off the line that produced it and <b>cannot abort the
 * computation</b> (M5.1, {@code D-TRACE} and O-27). A condition that must stop the engine is an
 * exception, and is not expressible here.
 */
public enum Severity {

    /** Worth saying, nothing to do. */
    INFO,

    /** The taxpayer should look at this, but the number stands. */
    ATTENTION,

    /** The number is provisional until the taxpayer does something — produce a receipt, elect. */
    ACTION_REQUIRED,

    /**
     * The rule behind this line is not fully verified.
     *
     * <p>Distinct from the other three because its subject is the <i>rule</i> rather than the
     * taxpayer's situation: it is what an {@code UNVERIFIED} {@code VerificationLevel} becomes when it reaches
     * a reader. A figure computed from an unverified rule is still computed and still shown — it is
     * labelled, not withheld.
     */
    UNVERIFIED_RULE
}
