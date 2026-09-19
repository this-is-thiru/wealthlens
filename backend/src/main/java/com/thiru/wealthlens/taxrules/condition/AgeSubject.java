package com.thiru.wealthlens.taxrules.condition;

/**
 * Whose age a rule turns on.
 *
 * <p>{@code DED-80D-007} allows the higher medical-insurance limit where <em>either</em> the
 * taxpayer <em>or</em> the person insured is sixty or over, which is two different questions about
 * two different people. Without this, both limbs of that rule's {@code AnyOf} are the same predicate
 * and the rule quietly becomes a test of the taxpayer's own age — see the erratum on
 * {@code 15-review-findings.md} Part E.
 */
public enum AgeSubject {

    /** The assessee. */
    SELF,

    /** The person covered by the policy or payment the rule is about. */
    INSURED_PERSON
}
