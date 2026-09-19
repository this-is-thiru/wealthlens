package com.thiru.wealthlens.taxrules.validate;

/**
 * A rule pack is malformed, and no computation may run against it.
 *
 * <p>Thrown at load time, never during a computation. That distinction is the point: a pack whose
 * slab bands leave a gap, or whose rule references a base it never defines, produces arithmetic that
 * is perfectly plausible and quietly wrong. Failing when the pack is read means the defect surfaces
 * once, in a deployment, rather than silently in every tax bill computed from it.
 *
 * <p>Unchecked, because there is no useful recovery. A caller cannot repair a pack it did not write.
 * {@code 02-TECH-SPEC.md} T5 names this as the failure type for every load-time invariant, and
 * {@code 19-engineering-foundations.md} E4 makes &ldquo;the loader throws nothing else&rdquo; a
 * property under test — so widening this hierarchy is a decision, not a convenience.
 */
public class PackValidationException extends RuntimeException {

    public PackValidationException(String message) {
        super(message);
    }

    /**
     * @param cause the domain constructor's own rejection, chained so that a genuine bug in a
     *     reader stays diagnosable rather than reading as a pack defect
     */
    public PackValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
